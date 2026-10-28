package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;

public record NetworkPrepared(String instanceId, PreparedNetwork resources) implements InstanceWorkflowEvent {}
