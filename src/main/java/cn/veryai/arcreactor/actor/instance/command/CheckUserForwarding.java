package cn.veryai.arcreactor.actor.instance.command;

public record CheckUserForwarding(String forwardingId, long generation) implements WorkflowCommand {}

