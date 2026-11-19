package cn.veryai.arcreactor.actor.instance;

import static org.springframework.util.StringUtils.hasText;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.FloatingIpSettings;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/** Cloud calls only; InstanceForwardingWorkflow handles persistence effects and retry scheduling. */
final class InstanceForwardingOperations {
  static final class InvalidForwardingResult extends IllegalStateException {
    InvalidForwardingResult() {
      super("Incomplete or mismatched port forwarding result");
    }
  }

  private final OpenStackClient client;
  private final FloatingIpForwardings forwardings;

  InstanceForwardingOperations(OpenStackClient client, FloatingIpForwardings forwardings) {
    this.client = client;
    this.forwardings = forwardings;
  }

  CompletionStage<ForwardingReply> requestCreation(InstanceWorkflowState state, InstanceForwardingState forwarding, boolean submit) {
    ForwardingSpec spec = targetSpec(state, forwarding);
    return askForwarding(() -> submit ? forwardings.create(forwarding.floatingIpId(), forwarding.claimId(), spec)
        : forwardings.get(forwarding.floatingIpId(), forwarding.claimId()))
        .thenCompose(reply -> ensureAccessAfterCreation(state, forwarding, spec, reply));
  }

  CompletionStage<ForwardingReply> requestDeletion(InstanceWorkflowState state, InstanceForwardingState forwarding,
      boolean submit) {
    String floatingIpId = forwarding.floatingIpId();
    String claimId = forwarding.claimId();
    CompletionStage<ForwardingReply> request;
    if (submit) {
      request = forwardings.delete(floatingIpId, claimId);
    } else {
      request = forwardings.get(floatingIpId, claimId).thenCompose(reply -> {
        if (reply == null) return CompletableFuture.completedFuture(null);
        boolean retryDelete = switch (reply.status()) {
          case READY, FAILED, NOT_FOUND -> true;
          case DELETED, IN_PROGRESS, REJECTED, DELETE_FAILED -> false;
        };
        return retryDelete ? forwardings.delete(floatingIpId, claimId) : CompletableFuture.completedFuture(reply);
      });
    }
    return request.thenCompose(reply -> cleanAccessAfterDeletion(state, forwarding.internalPort(), reply));
  }

  /** Query first on recovery; only an absent claim allows submitting its durable creation intent again. */
  CompletionStage<ForwardingReply> recoverForwardingCreation(InstanceWorkflowState state, InstanceForwardingState forwarding) {
    ForwardingSpec spec = targetSpec(state, forwarding);
    return askForwarding(() -> forwardings.get(forwarding.floatingIpId(), forwarding.claimId())
        .thenCompose(reply -> {
          if (reply == null || reply.status() != ForwardingReply.Status.NOT_FOUND) {
            return CompletableFuture.completedFuture(reply);
          }
          return forwardings.create(forwarding.floatingIpId(), forwarding.claimId(), spec);
        })).thenCompose(reply -> ensureAccessAfterCreation(state, forwarding, spec, reply));
  }

  private static ForwardingSpec targetSpec(InstanceWorkflowState state, InstanceForwardingState forwarding) {
    var network = state.networkPreparation().resources();
    return new ForwardingSpec(state.instanceId(), state.userId(), state.spec().region(), state.osProjectId(),
        network.osParentPortId(), network.parentFixedIp(), forwarding.internalPort(), forwarding.kind());
  }

  /** A transport failure leaves the claim outcome uncertain. The actor polls the same claim. */
  private static CompletionStage<ForwardingReply> askForwarding(Supplier<CompletionStage<ForwardingReply>> request) {
    try {
      return request.get().exceptionally(error -> null);
    } catch (RuntimeException error) {
      return CompletableFuture.completedFuture(null);
    }
  }

  private static boolean matchesReady(InstanceForwardingState forwarding, ForwardingSpec expected, ForwardingClaim claim) {
    if (claim == null || claim.phase() != ForwardingClaim.Phase.READY || !expected.equals(claim.spec())
        || !forwarding.claimId().equals(claim.claimId()) || !forwarding.floatingIpId().equals(claim.floatingIpId())
        || !hasText(claim.osFloatingIpId())) return false;
    var resources = claim.resources();
    return hasText(resources.floatingIpAddress()) && hasText(resources.osTcpForwardingId())
        && hasText(resources.osUdpForwardingId()) && resources.externalPort() >= FloatingIpSettings.FIRST_PORT
        && resources.externalPort() <= FloatingIpSettings.LAST_PORT;
  }

  private CompletionStage<ForwardingReply> ensureAccessAfterCreation(InstanceWorkflowState state,
      InstanceForwardingState forwarding, ForwardingSpec spec, ForwardingReply reply) {
    if (reply == null || reply.status() != ForwardingReply.Status.READY) {
      return CompletableFuture.completedFuture(reply);
    }
    if (!matchesReady(forwarding, spec, reply.claim())) throw new InvalidForwardingResult();
    return client.ensureInstanceForwardingAccess(target(state), state.instanceId(), state.osSecurityGroupId(),
        spec.internalPort()).thenApply(ignored -> reply);
  }

  private CompletionStage<ForwardingReply> cleanAccessAfterDeletion(InstanceWorkflowState state,
      int internalPort, ForwardingReply reply) {
    if (reply == null || reply.status() != ForwardingReply.Status.DELETED) {
      return CompletableFuture.completedFuture(reply);
    }
    return client.deleteInstanceForwardingAccess(target(state), state.instanceId(), state.osSecurityGroupId(),
        internalPort).thenApply(ignored -> reply);
  }

  private static OpenStackClient.ServerTarget target(InstanceWorkflowState state) {
    return new OpenStackClient.ServerTarget(state.spec().clusterId(), state.osProjectId(), state.spec().region());
  }
}
