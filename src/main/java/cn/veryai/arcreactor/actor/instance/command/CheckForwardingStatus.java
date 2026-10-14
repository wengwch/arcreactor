package cn.veryai.arcreactor.actor.instance.command;

public record CheckForwardingStatus(long generation, int attempt) implements WorkflowCommand {}
