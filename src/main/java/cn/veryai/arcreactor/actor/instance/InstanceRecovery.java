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
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Recovers one durable stage without invoking the actor's creation or deletion orchestration. */
public final class InstanceRecovery {
  private static final Logger log = LoggerFactory.getLogger(InstanceRecovery.class);

  public enum Action { RETRY, CONTINUE_CREATION, STOP }

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
        case PREPARING_DEPENDENCIES -> recoverDependencies(state);
        case PREPARING_SECURITY_GROUP -> recoverSecurityGroup(state);
        case PREPARING_PARENT_PORT -> recoverParentPort(state);
        case PREPARING_SUB_PORT -> recoverSubPort(state);
        case PREPARING_TRUNK -> recoverTrunk(state);
        case PREPARING_FORWARDING -> recoverForwarding(state);
        case PROVISIONING -> recoverProvisioning(state);
        case CONFIRMING -> recoverConfirmation(state);
        case DELETING -> recoverDeletion(state);
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
      if (result.status() == HypervisorPlacement.ScheduleResult.Status.NO_VALID_HOST) {
        return failed(state, "NO_VALID_HOST", result.detail());
      }
      Instant now = clock.instant();
      return proceed(new HypervisorSelected(state.instanceId(), result.hypervisorId(), reservationId(state),
              now),
          new DependencyPreparationStarted(state.instanceId()));
    });
  }

  private CompletionStage<Outcome> recoverDependencies(InstanceWorkflowState state) {
    var share = state.spec().share();
    return dependencies.prepare(state.userId(), state.spec().clusterId(), share.shareId(), share.spec(),
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
    // Discover the parent port and its QoS without resubmitting creation.
    return client.findInstanceParentPort(target(state), networkOperationId(state),
        state.dependencies().network(), state.osSecurityGroupId()).thenApply(resources -> {
          if (resources == null) return retry();
          return proceed(new ParentPortPrepared(state.instanceId(), resources),
              new SubPortPreparationStarted(state.instanceId()));
        });
  }

  private CompletionStage<Outcome> recoverSubPort(InstanceWorkflowState state) {
    // Discover the subport, its QoS and floating IP using the recorded parent port.
    return client.findInstanceSubPort(target(state), networkOperationId(state),
        state.dependencies().network(), state.osSecurityGroupId(), state.networkPreparation().resources())
        .thenApply(resources -> {
          if (resources == null) return retry();
          return proceed(new SubPortPrepared(state.instanceId(), resources),
              new TrunkPreparationStarted(state.instanceId()));
        });
  }

  private CompletionStage<Outcome> recoverTrunk(InstanceWorkflowState state) {
    // Discover the trunk using the recorded ports without resubmitting creation.
    return client.findInstanceTrunk(target(state), networkOperationId(state),
        state.dependencies().network(), state.networkPreparation().resources()).thenApply(resources -> {
          if (resources == null) return retry();
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
      if (server == null) return retry();
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

  private CompletionStage<Outcome> recoverDeletion(InstanceWorkflowState state) {
    // Recovery owns this ordering independently of normal externally requested deletion.
    return recoverNovaDeletion(state).thenCompose(osServerId -> {
      if (osServerId != null) {
        return CompletableFuture.completedFuture(state.novaDeletionAccepted() ? retry()
            : new Outcome(List.of(new NovaDeletionAccepted(state.instanceId(), osServerId, clock.instant())), Action.RETRY));
      }
      return recoverForwardingDeletion(state).thenCompose(deleted -> {
        if (!deleted) return CompletableFuture.completedFuture(false);
        return state.networkPreparation() == null ? CompletableFuture.completedFuture(true)
            : client.deleteInstanceNetwork(target(state), networkOperationId(state)).thenApply(ignored -> true);
      }).thenCompose(deleted -> {
        if (!deleted) return CompletableFuture.completedFuture(false);
        return state.securityGroupPreparation() == null ? CompletableFuture.completedFuture(true)
            : client.deleteInstanceSecurityGroup(target(state), state.instanceId()).thenApply(ignored -> true);
      }).thenCompose(deleted -> {
        if (!deleted) return CompletableFuture.completedFuture(false);
        return state.hypervisorId() == null || state.reservationId() == null ? CompletableFuture.completedFuture(true)
            : placement.cancel(state.hypervisorId(), state.reservationId())
                .thenApply(result -> result.status() == HypervisorPlacement.ReservationResult.Status.SUCCEEDED);
      }).thenApply(deleted -> deleted
          ? new Outcome(List.of(new InstanceDeleted(state.instanceId(), clock.instant())), Action.STOP) : retry());
    });
  }

  private CompletionStage<String> recoverNovaDeletion(InstanceWorkflowState state) {
    if (state.osNovaServerId() == null && state.novaOperationId() == null) return CompletableFuture.completedFuture(null);
    CompletionStage<OpenStackClient.NovaServer> lookup = state.osNovaServerId() == null
        ? client.findServerByOperationId(target(state), state.novaOperationId()).thenApply(OpenStackClient.ServerLookupResult::server)
        : client.getServer(target(state), state.osNovaServerId());
    return lookup.thenCompose(server -> {
      if (server == null) return CompletableFuture.completedFuture(null);
      // A journaled acceptance survives restarts. Recovery keeps querying until Nova reports absence.
      if (state.novaDeletionAccepted()) return CompletableFuture.completedFuture(server.osServerId());
      return client.deleteServer(target(state), server.osServerId()).thenApply(ignored -> server.osServerId());
    });
  }

  private CompletionStage<Boolean> recoverForwardingDeletion(InstanceWorkflowState state) {
    if (state.spec().forwarding() == null || !state.forwardingPreparationStarted()) {
      return CompletableFuture.completedFuture(true);
    }
    List<CompletableFuture<Boolean>> requests = new ArrayList<>();
    for (String osFloatingIpId : state.spec().forwarding().osFloatingIpIds()) {
      for (int port : state.spec().forwarding().internalPorts()) {
        requests.add(forwardings.delete(state.spec().region(), osFloatingIpId, forwardingClaimId(state, port))
            .handle((reply, error) -> error == null && reply != null && reply.status() == ForwardingReply.Status.DELETED)
            .toCompletableFuture());
      }
    }
    return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> requests.stream().allMatch(CompletableFuture::join));
  }

  private Outcome failedRequest(InstanceWorkflowState state, Throwable error) {
    return switch (state.phase()) {
      case SCHEDULING -> failed(state, "SCHEDULING_FAILED", error.toString());
      case PREPARING_DEPENDENCIES -> {
        String code = error instanceof BaseException business ? Integer.toString(business.getCode()) : "DEPENDENCY_PREPARATION_FAILED";
        yield failed(state, code, "Dependency preparation failed: " + code + " (" + error.getClass().getSimpleName() + ")");
      }
      case PREPARING_SECURITY_GROUP -> failed(state, "SECURITY_GROUP_PREPARATION_FAILED", error instanceof OpenStackOperationException
              ? error.getMessage() : "Security group preparation failed: " + error.getClass().getSimpleName());
      case PREPARING_PARENT_PORT, PREPARING_SUB_PORT, PREPARING_TRUNK -> error instanceof OpenStackOperationException
          ? failed(state, "NETWORK_PREPARATION_FAILED", error.toString())
          : retry();
      case DELETING -> {
        log.warn("Instance deletion recovery incomplete: instanceId={} reason={}", state.instanceId(), error.toString());
        yield retry();
      }
      default -> retry();
    };
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
