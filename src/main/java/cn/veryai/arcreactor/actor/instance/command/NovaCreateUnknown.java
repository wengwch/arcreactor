package cn.veryai.arcreactor.actor.instance.command;

public record NovaCreateUnknown(String operationId, String reason) implements WorkflowCommand {
}
