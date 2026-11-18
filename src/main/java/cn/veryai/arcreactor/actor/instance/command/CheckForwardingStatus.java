package cn.veryai.arcreactor.actor.instance.command;

public record CheckForwardingStatus(String claimId, long generation, int attempt) implements WorkflowCommand {}
