package cn.veryai.arcreactor.actor.instance.command;

public record ConfirmRejected(String reason) implements WorkflowCommand {
}
