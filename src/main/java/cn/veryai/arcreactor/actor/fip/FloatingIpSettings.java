package cn.veryai.arcreactor.actor.fip;

import java.time.Duration;
import java.util.Objects;

public record FloatingIpSettings(Duration retryDelay, Duration operationTimeout, int maxAttempts) {
  public static final int FIRST_PORT = 10000;
  public static final int LAST_PORT = 40000;

  public FloatingIpSettings {
    Objects.requireNonNull(retryDelay); Objects.requireNonNull(operationTimeout);
    if (maxAttempts < 1 || retryDelay.isZero() || retryDelay.isNegative()
        || operationTimeout.isZero() || operationTimeout.isNegative()) {
      throw new IllegalArgumentException("invalid floating IP settings");
    }
  }
}
