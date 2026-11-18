package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState;

public interface InstanceForwardingEvent extends InstanceWorkflowEvent {
  String instanceId();
  InstanceForwardingState forwarding();
}
