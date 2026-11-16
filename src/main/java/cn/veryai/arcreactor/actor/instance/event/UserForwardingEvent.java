package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.UserForwarding;

public interface UserForwardingEvent extends InstanceWorkflowEvent {
  String instanceId();
  UserForwarding forwarding();
}

