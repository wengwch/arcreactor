package cn.veryai.arcreactor.actor.fip;

import java.time.Duration;
import java.util.Objects;

public record FloatingIpSettings(Duration outcomeRetention, Duration outcomeCleanupInterval) {
  public static final int FIRST_PORT = 10000;
  public static final int LAST_PORT = 40000;

  public FloatingIpSettings {
    Objects.requireNonNull(outcomeRetention, "outcomeRetention");
    Objects.requireNonNull(outcomeCleanupInterval, "outcomeCleanupInterval");
    if (outcomeRetention.isZero() || outcomeRetention.isNegative()
        || outcomeCleanupInterval.isZero() || outcomeCleanupInterval.isNegative()) {
      throw new IllegalArgumentException("forwarding outcome retention and cleanup interval must be positive");
    }
  }
}
