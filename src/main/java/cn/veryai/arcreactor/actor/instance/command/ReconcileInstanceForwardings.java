package cn.veryai.arcreactor.actor.instance.command;

/** Advances forwarding work only while the originating lifecycle generation and state version still match. */
public record ReconcileInstanceForwardings(long generation, long version) implements WorkflowCommand {}
