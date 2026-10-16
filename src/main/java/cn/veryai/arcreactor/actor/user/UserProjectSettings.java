package cn.veryai.arcreactor.actor.user;

import java.time.Duration;
import java.util.Objects;

public record UserProjectSettings(Duration retryDelay, Duration operationTimeout, int maxAttempts) {
  public UserProjectSettings {
    Objects.requireNonNull(retryDelay, "retryDelay");
    Objects.requireNonNull(operationTimeout, "operationTimeout");
    if (retryDelay.isZero() || retryDelay.isNegative() || operationTimeout.isZero()
        || operationTimeout.isNegative() || maxAttempts < 1) {
      throw new IllegalArgumentException("invalid project workflow settings");
    }
  }
  public static UserProjectSettings defaults() {
    return new UserProjectSettings(Duration.ofSeconds(2), Duration.ofSeconds(30), 15);
  }
}
