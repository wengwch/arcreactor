package cn.veryai.arcreactor.actor.user;

import java.time.Duration;
import java.util.Objects;

public record UserShareSettings(Duration pollInterval, int maxPollAttempts, Duration operationTimeout) {
  public UserShareSettings {
    Objects.requireNonNull(pollInterval, "pollInterval");
    Objects.requireNonNull(operationTimeout, "operationTimeout");
    if (pollInterval.isZero() || pollInterval.isNegative()) {
      throw new IllegalArgumentException("pollInterval must be positive");
    }
    if (operationTimeout.isZero() || operationTimeout.isNegative()) {
      throw new IllegalArgumentException("operationTimeout must be positive");
    }
    if (maxPollAttempts <= 0) throw new IllegalArgumentException("maxPollAttempts must be positive");
  }
}
