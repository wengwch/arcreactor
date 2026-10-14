package cn.veryai.arcreactor.actor.fip;

import java.time.Duration;
import java.util.Objects;

public record FloatingIpSettings(int portRangeStart, int portRangeEnd, Duration retryDelay,
    Duration operationTimeout, int maxAttempts) {
  public FloatingIpSettings {
    Objects.requireNonNull(retryDelay); Objects.requireNonNull(operationTimeout);
    if (portRangeStart < 1 || portRangeEnd > 65535 || portRangeStart > portRangeEnd
        || maxAttempts < 1 || retryDelay.isZero() || retryDelay.isNegative()
        || operationTimeout.isZero() || operationTimeout.isNegative()) {
      throw new IllegalArgumentException("invalid floating IP settings");
    }
  }
}
