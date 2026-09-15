package cn.veryai.arcreactor.actor.hypervisor.projection;

import java.time.Duration;

public interface ProjectionMetrics {
    ProjectionMetrics NOOP = new ProjectionMetrics() {};
    default void processed() {}
    default void lag(Duration duration) {}
}
