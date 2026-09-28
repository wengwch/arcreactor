package cn.veryai.arcreactor.actor.instance.command;

public record NovaReconcileUnknown(long generation, String reason) implements WorkflowCommand {
}
