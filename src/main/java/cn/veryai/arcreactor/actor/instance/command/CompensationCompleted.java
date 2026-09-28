package cn.veryai.arcreactor.actor.instance.command;

public record CompensationCompleted(long generation, boolean success, String reason) implements WorkflowCommand {
}
