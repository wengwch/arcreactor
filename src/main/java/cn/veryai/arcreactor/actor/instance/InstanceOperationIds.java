package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;

/** Stable resource identities shared by creation, recovery and explicit deletion. */
final class InstanceOperationIds {
  private InstanceOperationIds() {}

  static String reservationId(InstanceWorkflowState state) {
    return state.reservationId() == null
        ? "res:" + state.instanceId() + ":" + state.requestId() : state.reservationId();
  }

  static String operationId(InstanceWorkflowState state) {
    return state.novaOperationId() == null
        ? "nova-create:" + state.instanceId() + ":" + state.requestId() : state.novaOperationId();
  }

  static String networkOperationId(InstanceWorkflowState state) {
    return "network:" + state.instanceId() + ":" + state.requestId();
  }

  static String forwardingClaimId(InstanceWorkflowState state) {
    return forwardingClaimId(state, state.currentForwardingPort());
  }

  static String forwardingClaimId(InstanceWorkflowState state, int internalPort) {
    return "veryai:fip:" + state.instanceId() + ":" + state.requestId() + ":" + internalPort;
  }
}
