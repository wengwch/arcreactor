package cn.veryai.arcreactor.actor.instance.command;

/** Rechecks persisted forwarding state and advances the current instance lifecycle stage. */
public record ReconcileInstanceForwardings(long generation) implements WorkflowCommand {}
