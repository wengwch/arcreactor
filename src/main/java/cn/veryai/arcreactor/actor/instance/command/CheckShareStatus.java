package cn.veryai.arcreactor.actor.instance.command;

public record CheckShareStatus(long generation, int attempt) implements WorkflowCommand {}
