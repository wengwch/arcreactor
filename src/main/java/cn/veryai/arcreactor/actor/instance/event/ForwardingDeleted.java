package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState;

public record ForwardingDeleted(String instanceId, InstanceForwardingState forwarding)
    implements InstanceForwardingEvent {}
