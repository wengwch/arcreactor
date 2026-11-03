package cn.veryai.arcreactor.actor.user;

import java.time.Duration;
import java.util.Objects;

public record UserShareSettings(Duration pollInterval, Duration operationTimeout, Duration creationTimeout) {
  public UserShareSettings {
    Objects.requireNonNull(pollInterval, "pollInterval");
    Objects.requireNonNull(operationTimeout, "operationTimeout");
    Objects.requireNonNull(creationTimeout, "creationTimeout");
    if (pollInterval.isZero() || pollInterval.isNegative()) {
      throw new IllegalArgumentException("pollInterval must be positive");
    }
    if (operationTimeout.isZero() || operationTimeout.isNegative()) {
      throw new IllegalArgumentException("operationTimeout must be positive");
    }
    if (creationTimeout.isZero() || creationTimeout.isNegative()) {
      throw new IllegalArgumentException("creationTimeout must be positive");
    }
  }
}
