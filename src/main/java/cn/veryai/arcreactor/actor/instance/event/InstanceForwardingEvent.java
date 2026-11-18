package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingSpec;

public interface InstanceForwardingEvent extends InstanceWorkflowEvent {
  String instanceId();
  InstanceForwardingSpec spec();
}
