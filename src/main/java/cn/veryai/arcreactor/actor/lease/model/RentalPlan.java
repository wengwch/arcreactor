package cn.veryai.arcreactor.actor.lease.model;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

/** Fixed-duration rental periods. A month always contains thirty 24-hour days. */
public record RentalPlan(Unit unit, int quantity) implements Serializable {
    public enum Unit {
        HOUR(3_600), DAY(86_400), WEEK(604_800), MONTH(2_592_000);
        private final long seconds;
        Unit(long seconds) { this.seconds = seconds; }
        public long seconds() { return seconds; }
    }

    public RentalPlan {
        Objects.requireNonNull(unit, "unit");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }

    public Duration duration() { return Duration.ofSeconds(Math.multiplyExact(unit.seconds(), quantity)); }
}
