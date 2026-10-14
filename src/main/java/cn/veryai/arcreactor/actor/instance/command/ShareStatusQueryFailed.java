package cn.veryai.arcreactor.actor.instance.command;

public record ShareStatusQueryFailed(long generation, int attempt) implements WorkflowCommand {}
