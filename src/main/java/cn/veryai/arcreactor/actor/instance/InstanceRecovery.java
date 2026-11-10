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
  private final InstanceWorkflowSettings settings;
  private final Clock clock;

  InstanceRecovery(HypervisorPlacement placement, OpenStackClient client, FloatingIpForwardings forwardings,
      InstanceDependencyPreparer dependencies, InstanceWorkflowSettings settings, Clock clock) {
    this.placement = placement;
    this.client = client;
    this.forwardings = forwardings;
    this.dependencies = dependencies;
    this.settings = settings;
    this.clock = clock;
  }

  CompletionStage<Outcome> recover(InstanceWorkflowState state) {
    try {
      CompletionStage<Outcome> request = switch (state.phase()) {
        case SCHEDULING -> recoverScheduling(state);
        case PREPARING_DEPENDENCIES -> recoverDependencies(state);
        case PREPARING_SECURITY_GROUP -> recoverSecurityGroup(state);
        case PREPARING_PARENT_PORT, PREPARING_SUB_PORT, PREPARING_TRUNK -> recoverNetwork(state);
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

  Outcome resolveCompletion(InstanceWorkflowState state, Outcome outcome) {
    // Recheck in the actor mailbox: a remote result may have arrived before a deadline but waited in the queue.
    return switch (state.phase()) {
      case PREPARING_SECURITY_GROUP -> expired(state.securityGroupPreparation().deadline()) ? securityGroupTimedOut(state) : outcome;
      case PREPARING_FORWARDING -> expired(state.forwardingPreparationDeadline()) ? forwardingTimedOut(state) : outcome;
      case PREPARING_PARENT_PORT, PREPARING_SUB_PORT, PREPARING_TRUNK ->
          outcome.action() != Action.STOP && expired(state.networkPreparation().deadline()) ? networkTimedOut(state) : outcome;
      default -> outcome;
    };
  }

  private CompletionStage<Outcome> recoverScheduling(InstanceWorkflowState state) {
    var spec = state.spec();
    var request = new HypervisorPlacement.ScheduleRequest(state.requestId(), reservationId(state),
        state.instanceId(), new HypervisorPlacement.Resources(spec.vcpus(), spec.memoryGb(), spec.diskGb(), spec.gpuCount()),
        settings.reservationTtl(), null, spec.region(), spec.hypervisorType(), spec.hostname());
    return placement.schedule(request).thenApply(result -> {
      if (result.status() == HypervisorPlacement.ScheduleResult.Status.NO_VALID_HOST) {
        return failed(state, "NO_VALID_HOST", result.detail());
      }
      Instant now = clock.instant();
      return proceed(new HypervisorSelected(state.instanceId(), result.hypervisorId(), reservationId(state),
              result.attempts(), now),
          new DependencyPreparationStarted(state.instanceId(), dependencies.preparationDeadline(now)));
    });
  }

  private CompletionStage<Outcome> recoverDependencies(InstanceWorkflowState state) {
    var share = state.spec().share();
    return dependencies.prepare(share.userId(), state.spec().clusterId(), share.shareId(), share.spec(),
        state.spec().network(), state.dependencyPreparationDeadline()).thenApply(prepared ->
        proceed(new DependenciesPrepared(state.instanceId(), prepared),
            new SecurityGroupPreparationStarted(state.instanceId(), clock.instant().plus(settings.provisioningTimeout()))));
  }

  private CompletionStage<Outcome> recoverSecurityGroup(InstanceWorkflowState state) {
    if (expired(state.securityGroupPreparation().deadline())) {
      return CompletableFuture.completedFuture(securityGroupTimedOut(state));
    }
    List<Integer> ports = state.spec().forwarding() == null ? List.of() : state.spec().forwarding().internalPorts();
    return client.prepareInstanceSecurityGroup(target(state), securityGroupOperationId(state), ports,
        state.securityGroupPreparation().deadline()).thenApply(groupId -> {
      if (expired(state.securityGroupPreparation().deadline())) return securityGroupTimedOut(state);
      if (!hasText(groupId)) return failed(state, "SECURITY_GROUP_PREPARATION_FAILED", "Missing instance security group ID");
      return proceed(new SecurityGroupPrepared(state.instanceId(), groupId),
          new ParentPortPreparationStarted(state.instanceId(), clock.instant().plus(settings.provisioningTimeout())));
    });
  }

  private CompletionStage<Outcome> recoverNetwork(InstanceWorkflowState state) {
    if (expired(state.networkPreparation().deadline())) {
      return CompletableFuture.completedFuture(networkTimedOut(state));
    }
    // The current stage may already have committed remotely. Only discover it; never resubmit its create.
    CompletionStage<PreparedNetwork> lookup = switch (state.phase()) {
      case PREPARING_PARENT_PORT -> client.prepareInstanceParentPort(target(state), networkOperationId(state),
          state.dependencies().network(), state.securityGroupId(), false);
      case PREPARING_SUB_PORT -> client.prepareInstanceSubPort(target(state), networkOperationId(state),
          state.dependencies().network(), state.securityGroupId(), state.networkPreparation().resources(), false);
      case PREPARING_TRUNK -> client.prepareInstanceTrunk(target(state), networkOperationId(state),
          state.dependencies().network(), state.networkPreparation().resources(), false);
      default -> throw new IllegalStateException("Not recovering instance network: " + state.phase());
    };
    return lookup.thenApply(resources -> {
      if (expired(state.networkPreparation().deadline())) return networkTimedOut(state);
      if (resources == null) return retry();
      return switch (state.phase()) {
        case PREPARING_PARENT_PORT -> proceed(new ParentPortPrepared(state.instanceId(), resources),
            new SubPortPreparationStarted(state.instanceId()));
        case PREPARING_SUB_PORT -> proceed(new SubPortPrepared(state.instanceId(), resources),
            new TrunkPreparationStarted(state.instanceId()));
        case PREPARING_TRUNK -> proceed(new TrunkPrepared(state.instanceId(), resources),
            state.spec().forwarding() == null ? novaStarted(state)
                : new ForwardingPreparationStarted(state.instanceId(), clock.instant().plus(settings.forwardingPreparationTimeout())));
        default -> throw new IllegalStateException("Not recovering instance network: " + state.phase());
      };
    });
  }

  private CompletionStage<Outcome> recoverForwarding(InstanceWorkflowState state) {
    if (expired(state.forwardingPreparationDeadline())) {
      return CompletableFuture.completedFuture(forwardingTimedOut(state));
    }
    String floatingIpId = state.spec().forwarding().floatingIpIds().get(state.forwardingCandidateIndex());
    ForwardingSpec spec = forwardingSpec(state);
    return forwardings.get(state.spec().region(), floatingIpId, forwardingClaimId(state))
        .thenCompose(reply -> {
          if (reply != null && reply.status() == ForwardingReply.Status.NOT_FOUND
              && !expired(state.forwardingPreparationDeadline())) {
            return forwardings.create(floatingIpId, forwardingClaimId(state), spec);
          }
          return CompletableFuture.completedFuture(reply);
        }).thenApply(reply -> {
          if (expired(state.forwardingPreparationDeadline())) return forwardingTimedOut(state);
          if (reply == null) return retry();
          return switch (reply.status()) {
            case READY -> recoveredForwarding(state, floatingIpId, spec, reply.claim());
            case FAILED, DELETE_FAILED, REJECTED, DELETED -> {
              if (reply.claim() != null && reply.claim().ownsPort()) {
                yield failed(state, "FORWARDING_PREPARATION_FAILED", reply.detail());
              }
              int candidate = state.forwardingCandidateIndex() + 1;
              yield candidate >= state.spec().forwarding().floatingIpIds().size()
                  ? failed(state, "FORWARDING_PREPARATION_FAILED", "All floating IP candidates failed for internal port "
                      + state.currentForwardingPort() + ": " + reply.detail())
                  : proceed(new ForwardingCandidateSelected(state.instanceId(), candidate));
            }
            case NOT_FOUND, IN_PROGRESS -> retry();
          };
        });
  }

  private Outcome recoveredForwarding(InstanceWorkflowState state, String floatingIpId,
      ForwardingSpec spec, ForwardingClaim claim) {
    if (claim == null || claim.phase() != ForwardingClaim.Phase.READY
        || !forwardingClaimId(state).equals(claim.claimId()) || !spec.equals(claim.spec())
        || !floatingIpId.equals(claim.floatingIpId())
        || !hasText(claim.resources().tcpForwardingId()) || !hasText(claim.resources().udpForwardingId())
        || !hasText(claim.resources().floatingIpAddress()) || claim.resources().externalPort() < 10000
        || claim.resources().externalPort() > 40000) {
      return failed(state, "FORWARDING_RESULT_INVALID", "Incomplete or mismatched port forwarding result");
    }
    var prepared = new ForwardingPrepared(state.instanceId(), PreparedForwarding.from(claim), clock.instant());
    return state.preparedForwardings().size() + 1 == state.spec().forwarding().internalPorts().size()
        ? proceed(prepared, novaStarted(state)) : proceed(prepared);
  }

  private CompletionStage<Outcome> recoverProvisioning(InstanceWorkflowState state) {
    // An uncertain Nova submission is resolved by lookup even after its deadline; never boot another server.
    CompletionStage<OpenStackClient.NovaServer> lookup = state.novaServerId() == null
        ? client.findServerByOperationId(target(state), state.novaOperationId()).thenApply(OpenStackClient.ServerLookupResult::server)
        : client.getServer(target(state), state.novaServerId());
    return lookup.thenApply(server -> {
      boolean timedOut = expired(state.provisioningDeadline());
      if (server == null) return timedOut
          ? failed(state, "PROVISIONING_TIMEOUT", "Nova server was not found before deadline") : retry();
      Instant now = clock.instant();
      List<InstanceWorkflowEvent> events = new ArrayList<>();
      if (!server.serverId().equals(state.novaServerId()) || server.status() != state.novaStatus()) {
        events.add(new NovaServerCreated(state.instanceId(), state.novaOperationId(), server.serverId(), server.status(), now));
      }
      if (server.status() == OpenStackClient.NovaServer.Status.ACTIVE) {
        events.add(new NovaReachedActive(state.instanceId(), server.serverId(), now));
        return new Outcome(events, Action.CONTINUE_CREATION);
      }
      if (server.status() == OpenStackClient.NovaServer.Status.ERROR || timedOut) {
        events.add(new InstanceProvisioningFailed(state.instanceId(), timedOut ? "PROVISIONING_TIMEOUT" : "NOVA_ERROR",
            timedOut ? "Nova server did not become ACTIVE before deadline" : server.fault(), now));
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
            new InstanceActivated(state.instanceId(), state.novaServerId(), state.hypervisorId(), now)), Action.STOP);
        case REJECTED -> failed(state, "RESERVATION_CONFIRM_REJECTED", result.detail());
        case UNKNOWN -> retry();
      };
    });
  }

  private CompletionStage<Outcome> recoverDeletion(InstanceWorkflowState state) {
    // Recovery owns this ordering independently of normal externally requested deletion.
    return recoverNovaDeletion(state).thenCompose(serverId -> {
      if (serverId != null) {
        return CompletableFuture.completedFuture(state.novaDeletionAccepted() ? retry()
            : new Outcome(List.of(new NovaDeletionAccepted(state.instanceId(), serverId, clock.instant())), Action.RETRY));
      }
      return recoverForwardingDeletion(state).thenCompose(deleted -> {
        if (!deleted) return CompletableFuture.completedFuture(false);
        return state.networkPreparation() == null ? CompletableFuture.completedFuture(true)
            : client.deleteInstanceNetwork(target(state), networkOperationId(state)).thenApply(ignored -> true);
      }).thenCompose(deleted -> {
        if (!deleted) return CompletableFuture.completedFuture(false);
        return state.securityGroupPreparation() == null ? CompletableFuture.completedFuture(true)
            : client.deleteInstanceSecurityGroup(target(state), securityGroupOperationId(state)).thenApply(ignored -> true);
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
    if (state.novaServerId() == null && state.novaOperationId() == null) return CompletableFuture.completedFuture(null);
    CompletionStage<OpenStackClient.NovaServer> lookup = state.novaServerId() == null
        ? client.findServerByOperationId(target(state), state.novaOperationId()).thenApply(OpenStackClient.ServerLookupResult::server)
        : client.getServer(target(state), state.novaServerId());
    return lookup.thenCompose(server -> {
      if (server == null) return CompletableFuture.completedFuture(null);
      // A journaled acceptance survives restarts. Recovery keeps querying until Nova reports absence.
      if (state.novaDeletionAccepted()) return CompletableFuture.completedFuture(server.serverId());
      return client.deleteServer(target(state), server.serverId()).thenApply(ignored -> server.serverId());
    });
  }

  private CompletionStage<Boolean> recoverForwardingDeletion(InstanceWorkflowState state) {
    if (state.spec().forwarding() == null || state.forwardingPreparationDeadline() == null) {
      return CompletableFuture.completedFuture(true);
    }
    List<CompletableFuture<Boolean>> requests = new ArrayList<>();
    for (String floatingIpId : state.spec().forwarding().floatingIpIds()) {
      for (int port : state.spec().forwarding().internalPorts()) {
        requests.add(forwardings.delete(state.spec().region(), floatingIpId, forwardingClaimId(state, port))
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
      case PREPARING_SECURITY_GROUP -> expired(state.securityGroupPreparation().deadline()) ? securityGroupTimedOut(state)
          : failed(state, "SECURITY_GROUP_PREPARATION_FAILED", error instanceof OpenStackOperationException
              ? error.getMessage() : "Security group preparation failed: " + error.getClass().getSimpleName());
      case PREPARING_PARENT_PORT, PREPARING_SUB_PORT, PREPARING_TRUNK -> error instanceof OpenStackOperationException
          ? failed(state, "NETWORK_PREPARATION_FAILED", error.toString())
          : expired(state.networkPreparation().deadline()) ? networkTimedOut(state) : retry();
      case PREPARING_FORWARDING -> expired(state.forwardingPreparationDeadline()) ? forwardingTimedOut(state) : retry();
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

  private Outcome securityGroupTimedOut(InstanceWorkflowState state) {
    return failed(state, "SECURITY_GROUP_PREPARATION_TIMEOUT", "Timed out preparing instance security group");
  }

  private Outcome networkTimedOut(InstanceWorkflowState state) {
    return failed(state, "NETWORK_PREPARATION_TIMEOUT", "Timed out preparing instance network: " + state.phase());
  }

  private Outcome forwardingTimedOut(InstanceWorkflowState state) {
    return failed(state, "FORWARDING_PREPARATION_TIMEOUT", "Timed out preparing port forwarding");
  }

  private NovaCreationStarted novaStarted(InstanceWorkflowState state) {
    Instant now = clock.instant();
    return new NovaCreationStarted(state.instanceId(), operationId(state), now, now.plus(settings.provisioningTimeout()));
  }

  private static Outcome proceed(InstanceWorkflowEvent... events) {
    return new Outcome(List.of(events), Action.CONTINUE_CREATION);
  }

  private static Outcome retry() { return new Outcome(List.of(), Action.RETRY); }

  private boolean expired(Instant deadline) { return !clock.instant().isBefore(deadline); }

  private static boolean hasText(String value) { return value != null && !value.isBlank(); }

  private static OpenStackClient.ServerTarget target(InstanceWorkflowState state) {
    return new OpenStackClient.ServerTarget(state.spec().clusterId(), state.projectId(), state.spec().region());
  }

  private static ForwardingSpec forwardingSpec(InstanceWorkflowState state) {
    PreparedNetwork network = state.networkPreparation().resources();
    return new ForwardingSpec(state.instanceId(), state.spec().share().userId(), state.spec().region(),
        state.projectId(), network.parentPortId(), network.parentFixedIp(), state.currentForwardingPort());
  }

  private static Throwable rootCause(Throwable error) {
    while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) {
      error = error.getCause();
    }
    return error;
  }
}
