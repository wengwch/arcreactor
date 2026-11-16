package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.UserForwarding;

public record UserForwardingCreated(String instanceId, UserForwarding forwarding) implements UserForwardingEvent {}

