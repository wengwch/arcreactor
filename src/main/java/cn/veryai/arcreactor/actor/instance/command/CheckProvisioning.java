package cn.veryai.arcreactor.actor.instance.command;

public record CheckProvisioning(long generation, int attempt) implements WorkflowCommand {}
