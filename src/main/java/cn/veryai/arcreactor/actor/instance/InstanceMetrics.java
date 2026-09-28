package cn.veryai.arcreactor.actor.instance;

/** Extension point for Micrometer or another metrics backend. */
public interface InstanceMetrics {
    InstanceMetrics NOOP = new InstanceMetrics() {};

    default void workflowStarted() {}
    default void workflowActive() {}
    default void workflowFailed() {}
    default void reconciliation() {}
    default void compensation() {}
}
