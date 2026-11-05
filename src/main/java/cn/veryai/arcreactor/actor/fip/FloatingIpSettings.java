package cn.veryai.arcreactor.actor.fip;

import java.time.Duration;
import java.util.Objects;

public record FloatingIpSettings(Duration operationTimeout, boolean cleanupOnFailure) {
  public static final int FIRST_PORT = 10000;
  public static final int LAST_PORT = 40000;

  public FloatingIpSettings {
    Objects.requireNonNull(operationTimeout, "operationTimeout");
    if (operationTimeout.isZero() || operationTimeout.isNegative()) {
      throw new IllegalArgumentException("invalid floating IP operation timeout");
    }
  }
}
