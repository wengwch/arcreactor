package cn.veryai.arcreactor.actor.instance.command;

public record ReconcileNova(String operationId, long generation) implements WorkflowCommand {
}
