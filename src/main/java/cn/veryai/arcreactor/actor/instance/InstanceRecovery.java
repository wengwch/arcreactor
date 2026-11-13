package cn.veryai.arcreactor.actor.instance;

import static cn.veryai.arcreactor.actor.instance.InstanceOperationIds.*;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.instance.event.*;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import cn.veryai.arcreactor.service.InstanceDependencyPreparer;
import java.io.IOException;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.openstack4j.api.exceptions.ConnectionException;
import org.openstack4j.api.exceptions.ResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Recovers one durable stage without invoking the actor's creation or deletion orchestration. */
public final class InstanceRecovery {
  private static final Logger log = LoggerFactory.getLogger(InstanceRecovery.class);

  public enum Action { RETRY, CONTINUE_CREATION, CONTINUE_DELETION, STOP }

  /** Local recovery decision; only its domain events are written to the journal. */
  public record Outcome(List<InstanceWorkflowEvent> events, Action action) implements Serializable {
    public Outcome { events = List.copyOf(events); }
  }

  private final HypervisorPlacement placement;
  private final OpenStackClient client;
  private final FloatingIpForwardings forwardings;
  private final InstanceDependencyPreparer dependencies;
  private final Clock clock;

  InstanceRecovery(HypervisorPlacement placement, OpenStackClient client, FloatingIpForwardings forwardings,
      InstanceDependencyPreparer dependencies, Clock clock) {
    this.placement = placement;
    this.client = client;
    this.forwardings = forwardings;
    this.dependencies = dependencies;
    this.clock = clock;
  }

  CompletionStage<Outcome> recover(InstanceWorkflowState state) {
    try {
      CompletionStage<Outcome> request = switch (state.phase()) {
        case SCHEDULING -> recoverScheduling(state);
        case RESERVING_RESOURCES -> recoverReservation(state);
        case PREPARING_DEPENDENCIES -> recoverDependencies(state);
        case PREPARING_SECURITY_GROUP -> recoverSecurityGroup(state);
        case PREPARING_PARENT_PORT -> recoverParentPort(state);
        case PREPARING_SUB_PORT -> recoverSubPort(state);
        case PREPARING_TRUNK -> recoverTrunk(state);
        case PREPARING_FORWARDING -> recoverForwarding(state);
        case PROVISIONING -> recoverProvisioning(state);
        case CONFIRMING -> recoverConfirmation(state);
        case DELETING_NOVA -> recoverNovaDeletion(state);
        case DELETING_FORWARDINGS -> recoverForwardingDeletion(state);
        case DELETING_NETWORK -> recoverNetworkDeletion(state);
        case DELETING_SECURITY_GROUP -> recoverSecurityGroupDeletion(state);
        case CANCELLING_RESERVATION -> recoverReservationCancellation(state);
        case RELEASING_ALLOCATION -> recoverAllocationRelease(state);
        default -> CompletableFuture.completedFuture(new Outcome(List.of(), Action.STOP));
      };
      return request.exceptionally(error -> failedRequest(state, rootCause(error)));
    } catch (RuntimeException error) {
      return CompletableFuture.completedFuture(failedRequest(state, rootCause(error)));
    }
  }

  private CompletionStage<Outcome> recoverScheduling(InstanceWorkflowState state) {
    var spec = state.spec();
    var request = new HypervisorPlacement.ScheduleRequest(state.requestId(), reservationId(state),
        state.instanceId(), new HypervisorPlacement.Resources(spec.vcpus(), spec.memoryGb(), spec.diskGb(), spec.gpuCount()),
        null, spec.region(), spec.hypervisorType(), spec.hostname());
    return placement.schedule(request).thenApply(result -> {
      List<String> candidates = result.hypervisorIds();
      if (candidates.isEmpty()) {
        return failed(state, "NO_VALID_HOST", result.detail());
      }
      return proceed(new HypervisorReservationStarted(state.instanceId(), candidates.get(0), reservationId(state),
          candidates.subList(1, candidates.size()), clock.instant()));
    });
  }

  private CompletionStage<Outcome> recoverReservation(InstanceWorkflowState state) {
    var spec = state.spec();
    var request = new HypervisorPlacement.ScheduleRequest(state.requestId(), state.reservationId(),
        state.instanceId(), new HypervisorPlacement.Resources(spec.vcpus(), spec.memoryGb(), spec.diskGb(), spec.gpuCount()),
        null, spec.region(), spec.hypervisorType(), spec.hostname());
    // An uncertain reservation must stay on the persisted host until its result is known.
    return placement.reserve(state.hypervisorId(), request).thenApply(result -> {
      return switch (result.status()) {
        case ACCEPTED -> proceed(new HypervisorSelected(state.instanceId(), state.hypervisorId(),
                state.reservationId(), clock.instant()),
            new DependencyPreparationStarted(state.instanceId()));
        case UNAVAILABLE -> {
          List<String> candidates = state.remainingHypervisorIds();
          yield candidates.isEmpty()
              ? failed(state, "NO_VALID_HOST", result.detail())
              : proceed(new HypervisorReservationStarted(state.instanceId(), candidates.get(0), state.reservationId(),
                  candidates.subList(1, candidates.size()), clock.instant()));
        }
        case REJECTED -> failed(state, "RESERVATION_REJECTED", result.detail());
        case UNKNOWN -> retry();
      };
    });
  }

  private CompletionStage<Outcome> recoverDependencies(InstanceWorkflowState state) {
    var share = state.spec().share();
    return dependencies.prepare(state.userId(), state.spec().clusterId(), share.spec(),
        state.spec().network()).thenApply(prepared ->
        proceed(new DependenciesPrepared(state.instanceId(), prepared),
            new SecurityGroupPreparationStarted(state.instanceId())));
  }

  private CompletionStage<Outcome> recoverSecurityGroup(InstanceWorkflowState state) {
    List<Integer> ports = state.spec().forwarding() == null ? List.of() : state.spec().forwarding().internalPorts();
    return client.recoverInstanceSecurityGroup(target(state), state.instanceId(), ports).thenApply(groupId -> {
      if (!hasText(groupId)) return failed(state, "SECURITY_GROUP_PREPARATION_FAILED", "Missing instance security group ID");
      return proceed(new SecurityGroupPrepared(state.instanceId(), groupId),
          new ParentPortPreparationStarted(state.instanceId()));
    });
  }

  private CompletionStage<Outcome> recoverParentPort(InstanceWorkflowState state) {
    return client.recoverInstanceParentPort(target(state), networkOperationId(state),
        state.dependencies().network(), state.osSecurityGroupId()).thenApply(resources -> {
          if (resources == null) return failed(state, "NETWORK_RECOVERY_INCOMPLETE",
              "Parent port recovery returned no resource state");
          return proceed(new ParentPortPrepared(state.instanceId(), resources),
              new SubPortPreparationStarted(state.instanceId()));
        });
  }

  private CompletionStage<Outcome> recoverSubPort(InstanceWorkflowState state) {
    return client.recoverInstanceSubPort(target(state), networkOperationId(state),
        state.dependencies().network(), state.osSecurityGroupId(), state.networkPreparation().resources())
        .thenApply(resources -> {
          if (resources == null) return failed(state, "NETWORK_RECOVERY_INCOMPLETE",
              "Subport recovery returned no resource state");
          return proceed(new SubPortPrepared(state.instanceId(), resources),
              new TrunkPreparationStarted(state.instanceId()));
        });
  }

  private CompletionStage<Outcome> recoverTrunk(InstanceWorkflowState state) {
    return client.recoverInstanceTrunk(target(state), networkOperationId(state),
        state.dependencies().network(), state.networkPreparation().resources()).thenApply(resources -> {
          if (resources == null) return failed(state, "NETWORK_RECOVERY_INCOMPLETE",
              "Trunk recovery returned no resource state");
          return proceed(new TrunkPrepared(state.instanceId(), resources),
              state.spec().forwarding() == null ? novaStarted(state)
                  : new ForwardingPreparationStarted(state.instanceId()));
        });
  }

  private CompletionStage<Outcome> recoverForwarding(InstanceWorkflowState state) {
    String osFloatingIpId = state.spec().forwarding().osFloatingIpIds().get(state.forwardingCandidateIndex());
    ForwardingSpec spec = forwardingSpec(state);
    return forwardings.get(state.spec().region(), osFloatingIpId, forwardingClaimId(state))
        .thenCompose(reply -> {
          if (reply != null && reply.status() == ForwardingReply.Status.NOT_FOUND) {
            return forwardings.create(osFloatingIpId, forwardingClaimId(state), spec);
          }
          return CompletableFuture.completedFuture(reply);
        }).thenApply(reply -> {
          if (reply == null) return retry();
          return switch (reply.status()) {
            case READY -> recoveredForwarding(state, osFloatingIpId, spec, reply.claim());
            case FAILED, DELETE_FAILED, REJECTED, DELETED -> {
              if (reply.claim() != null && reply.claim().ownsPort()) {
                yield failed(state, "FORWARDING_PREPARATION_FAILED", reply.detail());
              }
              int candidate = state.forwardingCandidateIndex() + 1;
              yield candidate >= state.spec().forwarding().osFloatingIpIds().size()
                  ? failed(state, "FORWARDING_PREPARATION_FAILED", "All floating IP candidates failed for internal port "
                      + state.currentForwardingPort() + ": " + reply.detail())
                  : proceed(new ForwardingCandidateSelected(state.instanceId(), candidate));
            }
            case NOT_FOUND, IN_PROGRESS -> retry();
          };
        });
  }

  private Outcome recoveredForwarding(InstanceWorkflowState state, String osFloatingIpId,
      ForwardingSpec spec, ForwardingClaim claim) {
    if (claim == null || claim.phase() != ForwardingClaim.Phase.READY
        || !forwardingClaimId(state).equals(claim.claimId()) || !spec.equals(claim.spec())
        || !osFloatingIpId.equals(claim.osFloatingIpId())
        || !hasText(claim.resources().osTcpForwardingId()) || !hasText(claim.resources().osUdpForwardingId())
        || !hasText(claim.resources().floatingIpAddress()) || claim.resources().externalPort() < 10000
        || claim.resources().externalPort() > 40000) {
      return failed(state, "FORWARDING_RESULT_INVALID", "Incomplete or mismatched port forwarding result");
    }
    var prepared = new ForwardingPrepared(state.instanceId(), PreparedForwarding.from(claim), clock.instant());
    return state.preparedForwardings().size() + 1 == state.spec().forwarding().internalPorts().size()
        ? proceed(prepared, novaStarted(state)) : proceed(prepared);
  }

  private CompletionStage<Outcome> recoverProvisioning(InstanceWorkflowState state) {
    // Resolve an uncertain Nova submission by lookup; never boot another server.
    CompletionStage<OpenStackClient.NovaServer> lookup = state.osNovaServerId() == null
        ? client.findServerByOperationId(target(state), state.novaOperationId()).thenApply(OpenStackClient.ServerLookupResult::server)
        : client.getServer(target(state), state.osNovaServerId());
    return lookup.thenApply(server -> {
      if (server == null) {
        return state.osNovaServerId() == null
            ? failed(state, "NOVA_CREATION_UNCONFIRMED",
                "Nova creation outcome is unconfirmed; verify OpenStack manually before deleting retained resources. "
                    + "Recovery will not submit another server creation")
            : failed(state, "NOVA_SERVER_MISSING", "The recorded Nova server no longer exists; resources are retained");
      }
      Instant now = clock.instant();
      List<InstanceWorkflowEvent> events = new ArrayList<>();
      if (!server.osServerId().equals(state.osNovaServerId()) || server.status() != state.novaStatus()) {
        events.add(new NovaServerCreated(state.instanceId(), state.novaOperationId(), server.osServerId(), server.status(), now));
      }
      if (server.status() == OpenStackClient.NovaServer.Status.ACTIVE) {
        events.add(new NovaReachedActive(state.instanceId(), server.osServerId(), now));
        return new Outcome(events, Action.CONTINUE_CREATION);
      }
      if (server.status() == OpenStackClient.NovaServer.Status.ERROR) {
        events.add(new InstanceProvisioningFailed(state.instanceId(), "NOVA_ERROR", server.fault(), now));
        return new Outcome(events, Action.STOP);
      }
      return new Outcome(events, Action.RETRY);
    });
  }

  private CompletionStage<Outcome> recoverConfirmation(InstanceWorkflowState state) {
    return placement.confirm(state.hypervisorId(), state.reservationId(), state.instanceId()).thenApply(result -> {
      Instant now = clock.instant();
      return switch (result.status()) {
        case SUCCEEDED -> new Outcome(List.of(new ReservationConfirmedForInstance(state.instanceId(),
                state.hypervisorId(), state.reservationId(), now),
            new InstanceActivated(state.instanceId(), state.osNovaServerId(), state.hypervisorId(), now)), Action.STOP);
        case REJECTED -> failed(state, "RESERVATION_CONFIRM_REJECTED", result.detail());
        case UNKNOWN -> retry();
      };
    });
  }

  private CompletionStage<Outcome> recoverNovaDeletion(InstanceWorkflowState state) {
    return checkNovaDeletionStatus(state).thenCompose(osServerId -> {
      if (osServerId == null) {
        return CompletableFuture.completedFuture(continueDeletion(
            new NovaServerDeleted(state.instanceId(), clock.instant())));
      }
      return state.novaDeletionAccepted() ? CompletableFuture.completedFuture(retry())
          : recoverNovaDeletion(state, osServerId);
    });
  }

  private CompletionStage<String> checkNovaDeletionStatus(InstanceWorkflowState state) {
    if (state.osNovaServerId() == null && state.novaOperationId() == null) return CompletableFuture.completedFuture(null);
    CompletionStage<OpenStackClient.NovaServer> lookup = state.osNovaServerId() == null
        ? client.findServerByOperationId(target(state), state.novaOperationId()).thenApply(OpenStackClient.ServerLookupResult::server)
        : client.getServer(target(state), state.osNovaServerId());
    return lookup.thenApply(server -> server == null ? null : server.osServerId());
  }

  private CompletionStage<Outcome> recoverNovaDeletion(InstanceWorkflowState state, String osServerId) {
    return client.deleteServer(target(state), osServerId).thenApply(ignored -> new Outcome(
        List.of(new NovaDeletionAccepted(state.instanceId(), osServerId, clock.instant())), Action.RETRY));
  }

  private CompletionStage<Outcome> recoverForwardingDeletion(InstanceWorkflowState state) {
    List<CompletableFuture<Boolean>> requests = new ArrayList<>();
    for (PreparedForwarding forwarding : state.preparedForwardings()) {
      requests.add(checkForwardingDeletionStatus(state, forwarding).thenCompose(reply -> {
        if (reply == null) return CompletableFuture.completedFuture(false);
        return switch (reply.status()) {
          case DELETED -> CompletableFuture.completedFuture(true);
          case IN_PROGRESS, REJECTED -> CompletableFuture.completedFuture(false);
          case READY, FAILED, DELETE_FAILED, NOT_FOUND ->
              submitForwardingDeletion(state, forwarding).thenApply(deleted ->
                  deleted != null && deleted.status() == ForwardingReply.Status.DELETED);
        };
      }).toCompletableFuture());
    }
    return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> requests.stream().allMatch(CompletableFuture::join)
            ? continueDeletion(new InstanceForwardingsDeleted(state.instanceId(), clock.instant())) : retry());
  }

  private CompletionStage<ForwardingReply> checkForwardingDeletionStatus(
      InstanceWorkflowState state, PreparedForwarding forwarding) {
    return forwardings.get(state.spec().region(), forwarding.osFloatingIpId(), forwarding.claimId());
  }

  private CompletionStage<ForwardingReply> submitForwardingDeletion(
      InstanceWorkflowState state, PreparedForwarding forwarding) {
    return forwardings.delete(state.spec().region(), forwarding.osFloatingIpId(), forwarding.claimId());
  }

  private CompletionStage<Outcome> recoverNetworkDeletion(InstanceWorkflowState state) {
    CompletionStage<Void> request = state.networkPreparation() == null
        ? CompletableFuture.completedFuture(null)
        : client.deleteInstanceNetwork(target(state), networkOperationId(state));
    return request.thenApply(ignored -> continueDeletion(new InstanceNetworkDeleted(state.instanceId(), clock.instant())));
  }

  private CompletionStage<Outcome> recoverSecurityGroupDeletion(InstanceWorkflowState state) {
    CompletionStage<Void> request = state.securityGroupPreparation() == null
        ? CompletableFuture.completedFuture(null)
        : client.deleteInstanceSecurityGroup(target(state), state.instanceId());
    return request.thenApply(ignored -> continueDeletion(
        new InstanceSecurityGroupDeleted(state.instanceId(), clock.instant())));
  }

  private CompletionStage<Outcome> recoverReservationCancellation(InstanceWorkflowState state) {
    if (state.hypervisorId() == null || state.reservationId() == null) {
      return CompletableFuture.completedFuture(continueDeletion(
          new InstanceReservationCancelled(state.instanceId(), clock.instant())));
    }
    return placement.cancel(state.hypervisorId(), state.reservationId()).thenApply(result ->
        result.status() == HypervisorPlacement.ReservationResult.Status.SUCCEEDED
            ? continueDeletion(new InstanceReservationCancelled(state.instanceId(), clock.instant())) : retry());
  }

  private CompletionStage<Outcome> recoverAllocationRelease(InstanceWorkflowState state) {
    if (state.hypervisorId() == null) {
      return CompletableFuture.completedFuture(new Outcome(
          List.of(new InstanceDeleted(state.instanceId(), clock.instant())), Action.STOP));
    }
    return placement.release(state.hypervisorId(), state.instanceId()).thenApply(result ->
        result.status() == HypervisorPlacement.ReservationResult.Status.SUCCEEDED
            ? new Outcome(List.of(new InstanceDeleted(state.instanceId(), clock.instant())), Action.STOP) : retry());
  }

  private Outcome failedRequest(InstanceWorkflowState state, Throwable error) {
    if (state.phase().isDeleting()) {
      log.warn("Instance deletion recovery incomplete: instanceId={} phase={} reason={}",
          state.instanceId(), state.phase(), error.toString());
      return retry();
    }
    if (hasTransientCause(error)) return retry();
    return switch (state.phase()) {
      case SCHEDULING -> failed(state, "SCHEDULING_FAILED", error.toString());
      case RESERVING_RESOURCES -> {
        log.warn("Instance reservation recovery incomplete: instanceId={} hypervisorId={} reservationId={} reason={}",
            state.instanceId(), state.hypervisorId(), state.reservationId(), error.toString());
        yield retry();
      }
      case PREPARING_DEPENDENCIES -> {
        String code = error instanceof BaseException business ? Integer.toString(business.getCode()) : "DEPENDENCY_PREPARATION_FAILED";
        yield failed(state, code, "Dependency preparation failed: " + code + " (" + error.getClass().getSimpleName() + ")");
      }
      case PREPARING_SECURITY_GROUP -> hasDefinitiveCause(error)
          ? failed(state, "SECURITY_GROUP_PREPARATION_FAILED", error.toString()) : retry();
      case PREPARING_PARENT_PORT, PREPARING_SUB_PORT, PREPARING_TRUNK -> hasDefinitiveCause(error)
          ? failed(state, "NETWORK_PREPARATION_FAILED", error.toString())
          : retry();
      case PROVISIONING -> hasDefinitiveCause(error)
          ? failed(state, "NOVA_RECOVERY_FAILED", error.toString()) : retry();
      default -> retry();
    };
  }

  private static boolean hasTransientCause(Throwable error) {
    Set<Throwable> visited = new HashSet<>();
    for (Throwable cause = error; cause != null && visited.add(cause); cause = cause.getCause()) {
      if (cause instanceof IOException || cause instanceof TimeoutException
          || cause instanceof InterruptedException || cause instanceof ConnectionException) return true;
      if (cause instanceof ResponseException response) {
        int status = response.getStatus();
        if (status == 408 || status == 429 || (status >= 500 && status < 600)) return true;
      }
    }
    return false;
  }

  private static boolean hasDefinitiveCause(Throwable error) {
    Set<Throwable> visited = new HashSet<>();
    for (Throwable cause = error; cause != null && visited.add(cause); cause = cause.getCause()) {
      if (cause instanceof OpenStackOperationException || cause instanceof BaseException
          || cause instanceof IllegalArgumentException || cause instanceof IllegalStateException
          || cause instanceof NullPointerException || cause instanceof IndexOutOfBoundsException) return true;
      if (cause instanceof ResponseException response
          && response.getStatus() >= 400 && response.getStatus() < 500) return true;
    }
    return false;
  }

  private Outcome failed(InstanceWorkflowState state, String code, String detail) {
    return new Outcome(List.of(new InstanceProvisioningFailed(state.instanceId(), code, detail, clock.instant())), Action.STOP);
  }

  private NovaCreationStarted novaStarted(InstanceWorkflowState state) {
    Instant now = clock.instant();
    return new NovaCreationStarted(state.instanceId(), operationId(state), now);
  }

  private static Outcome proceed(InstanceWorkflowEvent... events) {
    return new Outcome(List.of(events), Action.CONTINUE_CREATION);
  }

  private static Outcome continueDeletion(InstanceWorkflowEvent... events) {
    return new Outcome(List.of(events), Action.CONTINUE_DELETION);
  }

  private static Outcome retry() { return new Outcome(List.of(), Action.RETRY); }

  private static boolean hasText(String value) { return value != null && !value.isBlank(); }

  private static OpenStackClient.ServerTarget target(InstanceWorkflowState state) {
    return new OpenStackClient.ServerTarget(state.spec().clusterId(), state.osProjectId(), state.spec().region());
  }

  private static ForwardingSpec forwardingSpec(InstanceWorkflowState state) {
    PreparedNetwork network = state.networkPreparation().resources();
    return new ForwardingSpec(state.instanceId(), state.userId(), state.spec().region(),
        state.osProjectId(), network.osParentPortId(), network.parentFixedIp(), state.currentForwardingPort());
  }

  private static Throwable rootCause(Throwable error) {
    while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
      error = error.getCause();
    }
    return error;
  }
}
