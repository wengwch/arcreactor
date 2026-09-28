package cn.veryai.arcreactor.actor.instance.command;

public record ConfirmSucceeded(boolean idempotent) implements WorkflowCommand {
}
