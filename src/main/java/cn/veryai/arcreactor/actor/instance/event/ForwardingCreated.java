package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingSpec;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;

public record ForwardingCreated(String instanceId, InstanceForwardingSpec spec, PreparedForwarding resources)
    implements InstanceForwardingEvent {}
