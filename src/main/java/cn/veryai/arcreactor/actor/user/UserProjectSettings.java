package cn.veryai.arcreactor.actor.user;

import java.time.Duration;
import java.util.Objects;

public record UserProjectSettings(Duration operationTimeout) {
  public UserProjectSettings {
    Objects.requireNonNull(operationTimeout, "operationTimeout");
    if (operationTimeout.isZero() || operationTimeout.isNegative()) {
      throw new IllegalArgumentException("invalid project workflow settings");
    }
  }
  public static UserProjectSettings defaults() {
    return new UserProjectSettings(Duration.ofSeconds(30));
  }
}
