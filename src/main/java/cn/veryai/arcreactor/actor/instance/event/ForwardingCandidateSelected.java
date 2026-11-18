package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState;

/** The previous candidate has failed and completed cleanup before advancing. */
public record ForwardingCandidateSelected(String instanceId, InstanceForwardingState forwarding) implements InstanceForwardingEvent {}
