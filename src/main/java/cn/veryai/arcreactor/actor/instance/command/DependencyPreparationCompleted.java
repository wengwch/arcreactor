package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.PreparedDependencies;

public record DependencyPreparationCompleted(long generation, String requestToken, PreparedDependencies dependencies,
    String failureCode, String failure) implements WorkflowCommand {}
