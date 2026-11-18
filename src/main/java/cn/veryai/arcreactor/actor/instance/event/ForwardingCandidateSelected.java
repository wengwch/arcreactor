package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingSpec;

/** The previous candidate has failed and completed cleanup before advancing. */
public record ForwardingCandidateSelected(String instanceId, InstanceForwardingSpec spec) implements InstanceForwardingEvent {}
