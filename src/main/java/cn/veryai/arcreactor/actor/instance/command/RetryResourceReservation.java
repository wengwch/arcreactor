package cn.veryai.arcreactor.actor.instance.command;

public record RetryResourceReservation(long generation, int retryCount) implements WorkflowCommand {
}
