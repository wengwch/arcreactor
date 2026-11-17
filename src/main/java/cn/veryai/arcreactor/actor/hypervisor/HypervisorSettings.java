package cn.veryai.arcreactor.actor.hypervisor;

import java.time.Duration;
import java.util.Objects;

public record HypervisorSettings(Duration outcomeRetention, Duration outcomeCleanupInterval) {
    public HypervisorSettings {
        Objects.requireNonNull(outcomeRetention, "outcomeRetention");
        Objects.requireNonNull(outcomeCleanupInterval, "outcomeCleanupInterval");
        if (outcomeRetention.isNegative() || outcomeRetention.isZero()
                || outcomeCleanupInterval.isNegative() || outcomeCleanupInterval.isZero()) {
            throw new IllegalArgumentException("reservation outcome retention and cleanup interval must be positive");
        }
    }
}
