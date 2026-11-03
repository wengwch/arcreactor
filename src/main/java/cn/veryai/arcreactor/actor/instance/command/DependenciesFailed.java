package cn.veryai.arcreactor.actor.instance.command;

public record DependenciesFailed(String requestToken, String failureCode, String detail) implements WorkflowCommand {}
