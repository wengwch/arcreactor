package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.PreparedDependencies;

public record DependenciesReady(String requestToken, PreparedDependencies dependencies) implements WorkflowCommand {}
