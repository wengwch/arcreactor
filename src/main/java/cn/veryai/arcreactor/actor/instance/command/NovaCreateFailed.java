package cn.veryai.arcreactor.actor.instance.command;

public record NovaCreateFailed(String reason) implements WorkflowCommand {
}
