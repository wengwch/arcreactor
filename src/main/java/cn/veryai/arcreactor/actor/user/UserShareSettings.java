package cn.veryai.arcreactor.actor.user;

import java.time.Duration;
import java.util.Objects;

public record UserShareSettings(Duration pollInterval) {
  public UserShareSettings {
    Objects.requireNonNull(pollInterval, "pollInterval");
    if (pollInterval.isZero() || pollInterval.isNegative()) {
      throw new IllegalArgumentException("pollInterval must be positive");
    }
  }
}
