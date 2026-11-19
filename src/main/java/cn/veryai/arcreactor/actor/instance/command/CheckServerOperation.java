package cn.veryai.arcreactor.actor.instance.command;

public record CheckServerOperation(String operationId, int attempt) implements WorkflowCommand {}
