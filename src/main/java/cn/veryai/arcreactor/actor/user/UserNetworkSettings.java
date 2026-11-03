package cn.veryai.arcreactor.actor.user;

import java.time.Duration;
import java.util.Objects;

public record UserNetworkSettings(Duration operationTimeout) {
  public UserNetworkSettings {
    Objects.requireNonNull(operationTimeout, "operationTimeout");
    if (operationTimeout.isZero() || operationTimeout.isNegative()) {
      throw new IllegalArgumentException("invalid network workflow settings");
    }
  }
  public static UserNetworkSettings defaults() {
    return new UserNetworkSettings(Duration.ofSeconds(30));
  }
}
