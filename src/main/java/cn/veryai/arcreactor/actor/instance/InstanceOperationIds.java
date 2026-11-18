package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;

/** Stable resource identities shared by creation, recovery and explicit deletion. */
public final class InstanceOperationIds {
  private InstanceOperationIds() {}

  static String reservationId(InstanceWorkflowState state) {
    return state.reservationId() == null ? state.instanceId() : state.reservationId();
  }

  static String operationId(InstanceWorkflowState state) {
    return state.novaOperationId() == null
        ? "nova-create:" + state.instanceId() + ":" + state.requestId()
        : state.novaOperationId();
  }

  static String networkOperationId(InstanceWorkflowState state) {
    return "network:" + state.instanceId() + ":" + state.requestId();
  }

}
