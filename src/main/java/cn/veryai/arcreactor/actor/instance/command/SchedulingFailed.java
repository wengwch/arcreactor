package cn.veryai.arcreactor.actor.instance.command;

public record SchedulingFailed(String reason) implements WorkflowCommand {
}
