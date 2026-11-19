package cn.veryai.arcreactor.actor.instance.command;

/** A forwarding result was persisted; the instance may advance its current lifecycle stage. */
public record InstanceForwardingsChanged(long generation) implements WorkflowCommand {}
