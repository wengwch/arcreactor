package cn.veryai.arcreactor.actor.instance.command;

public record CheckDeletion(long generation, long attempt) implements WorkflowCommand {
}
