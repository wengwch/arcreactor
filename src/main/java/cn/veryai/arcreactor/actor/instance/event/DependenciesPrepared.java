package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.PreparedDependencies;

public record DependenciesPrepared(String instanceId, PreparedDependencies dependencies) implements InstanceWorkflowEvent {}
