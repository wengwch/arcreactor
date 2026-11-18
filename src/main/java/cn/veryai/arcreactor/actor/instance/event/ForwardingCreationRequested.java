package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingSpec;

public record ForwardingCreationRequested(String instanceId, InstanceForwardingSpec spec)
    implements InstanceForwardingEvent {}
