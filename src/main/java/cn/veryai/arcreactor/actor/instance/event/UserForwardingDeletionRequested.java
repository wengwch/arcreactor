package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.UserForwarding;

public record UserForwardingDeletionRequested(String instanceId, UserForwarding forwarding) implements UserForwardingEvent {}

