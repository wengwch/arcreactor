package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;

public record ParentPortPrepared(String instanceId, PreparedNetwork resources) implements InstanceWorkflowEvent {}
