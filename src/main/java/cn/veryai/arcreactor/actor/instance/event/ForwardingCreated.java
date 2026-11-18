package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;

public record ForwardingCreated(String instanceId, InstanceForwardingState forwarding, PreparedForwarding resources)
    implements InstanceForwardingEvent {}
