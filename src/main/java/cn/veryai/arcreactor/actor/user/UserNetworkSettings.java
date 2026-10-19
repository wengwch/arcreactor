package cn.veryai.arcreactor.actor.user;

import java.time.Duration;
import java.util.Objects;

public record UserNetworkSettings(Duration retryDelay, Duration operationTimeout, int maxAttempts) {
  public UserNetworkSettings {
    Objects.requireNonNull(retryDelay, "retryDelay");
    Objects.requireNonNull(operationTimeout, "operationTimeout");
    if (retryDelay.isZero() || retryDelay.isNegative() || operationTimeout.isZero()
        || operationTimeout.isNegative() || maxAttempts < 1) {
      throw new IllegalArgumentException("invalid network workflow settings");
    }
  }
  public static UserNetworkSettings defaults() {
    return new UserNetworkSettings(Duration.ofSeconds(2), Duration.ofSeconds(30), 15);
  }
}
