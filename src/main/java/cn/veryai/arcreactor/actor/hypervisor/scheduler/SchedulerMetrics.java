package cn.veryai.arcreactor.actor.hypervisor.scheduler;

public interface SchedulerMetrics {
    SchedulerMetrics NOOP = new SchedulerMetrics() {};
    /** A candidate query was requested. */
    default void request() {}
    /** A reservation on a specified host was accepted. */
    default void success() {}
    /** A candidate query found no eligible hosts. */
    default void noValidHost() {}
}
