package cn.veryai.arcreactor.actor.instance.command;

/** Internal creation-stage command; prepares the configured system forwardings. */
public record PrepareInstanceForwardings(long generation) implements WorkflowCommand {}
