package cn.veryai.arcreactor.actor.instance.command;

/** Internal deletion-stage command; cleans up all remaining forwardings. */
public record DeleteInstanceForwardings(long generation) implements WorkflowCommand {}
