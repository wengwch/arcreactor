package cn.veryai.arcreactor.actor.instance.command;

public record CheckProjectStatus(long generation, int attempt) implements WorkflowCommand {}
