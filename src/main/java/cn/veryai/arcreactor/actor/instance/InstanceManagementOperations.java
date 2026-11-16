package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.UserForwarding;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Cloud calls only; the instance actor owns durable intents, results and retry scheduling. */
final class InstanceManagementOperations {
  private final OpenStackClient client;
  private final FloatingIpForwardings forwardings;

  InstanceManagementOperations(OpenStackClient client, FloatingIpForwardings forwardings) {
    this.client = client;
    this.forwardings = forwardings;
  }

  CompletionStage<ForwardingReply> createForwarding(InstanceWorkflowState state, UserForwarding forwarding) {
    return client.ensureInstanceForwardingAccess(target(state), state.instanceId(), state.osSecurityGroupId(),
        forwarding.internalPort()).thenCompose(ignored -> forwardings.create(
            forwarding.osFloatingIpId(), forwarding.forwardingId(), spec(state, forwarding)));
  }

  CompletionStage<ForwardingReply> checkForwardingCreation(InstanceWorkflowState state, UserForwarding forwarding) {
    return forwardings.get(state.spec().region(), forwarding.osFloatingIpId(), forwarding.forwardingId());
  }

  CompletionStage<ForwardingReply> deleteForwarding(InstanceWorkflowState state, UserForwarding forwarding) {
    return forwardings.delete(state.spec().region(), forwarding.osFloatingIpId(), forwarding.forwardingId())
        .thenCompose(reply -> cleanAccessAfterDeletion(state, forwarding, reply));
  }

  CompletionStage<ForwardingReply> checkForwardingDeletion(InstanceWorkflowState state, UserForwarding forwarding) {
    return forwardings.get(state.spec().region(), forwarding.osFloatingIpId(), forwarding.forwardingId())
        .thenCompose(reply -> cleanAccessAfterDeletion(state, forwarding, reply));
  }

  /** Query first on recovery; only an absent claim allows submitting its durable creation intent again. */
  CompletionStage<ForwardingReply> recoverForwardingCreation(InstanceWorkflowState state, UserForwarding forwarding) {
    return forwardings.get(state.spec().region(), forwarding.osFloatingIpId(), forwarding.forwardingId())
        .thenCompose(reply -> {
          if (reply == null || reply.status() != ForwardingReply.Status.NOT_FOUND) {
            return CompletableFuture.completedFuture(reply);
          }
          return client.ensureInstanceForwardingAccess(target(state), state.instanceId(), state.osSecurityGroupId(),
              forwarding.internalPort()).thenCompose(ignored -> forwardings.create(
                  forwarding.osFloatingIpId(), forwarding.forwardingId(), spec(state, forwarding)));
        });
  }

  /** A persisted deletion is resumed independently of normal submission. */
  CompletionStage<ForwardingReply> recoverForwardingDeletion(InstanceWorkflowState state, UserForwarding forwarding) {
    return forwardings.get(state.spec().region(), forwarding.osFloatingIpId(), forwarding.forwardingId())
        .thenCompose(reply -> {
          if (reply == null || reply.status() == ForwardingReply.Status.IN_PROGRESS) {
            return CompletableFuture.completedFuture(reply);
          }
          if (reply.status() == ForwardingReply.Status.DELETED) {
            return cleanAccessAfterDeletion(state, forwarding, reply);
          }
          return forwardings.delete(state.spec().region(), forwarding.osFloatingIpId(), forwarding.forwardingId())
              .thenCompose(deleted -> cleanAccessAfterDeletion(state, forwarding, deleted));
        });
  }

  private CompletionStage<ForwardingReply> cleanAccessAfterDeletion(InstanceWorkflowState state,
      UserForwarding forwarding, ForwardingReply reply) {
    if (reply == null || reply.status() != ForwardingReply.Status.DELETED) {
      return CompletableFuture.completedFuture(reply);
    }
    return client.deleteInstanceForwardingAccess(target(state), state.instanceId(), state.osSecurityGroupId(),
        forwarding.internalPort()).thenApply(ignored -> reply);
  }

  static ForwardingSpec spec(InstanceWorkflowState state, UserForwarding forwarding) {
    var network = state.networkPreparation().resources();
    return new ForwardingSpec(state.instanceId(), state.userId(), state.spec().region(), state.osProjectId(),
        network.osParentPortId(), network.parentFixedIp(), forwarding.internalPort(), ForwardingKind.USER);
  }

  private static OpenStackClient.ServerTarget target(InstanceWorkflowState state) {
    return new OpenStackClient.ServerTarget(state.spec().clusterId(), state.osProjectId(), state.spec().region());
  }
}
