package cn.veryai.arcreactor.actor.hypervisor.scheduler;

public interface SchedulerMetrics {
    SchedulerMetrics NOOP = new SchedulerMetrics() {};
    default void request() {}
    default void success() {}
    default void noValidHost() {}
    default void claimRetry() {}
}
