package cn.veryai.arcreactor.actor.fip.event;

import java.util.Objects;

/** Imported port reservations remain durable even before the first forwarding claim is created. */
public record FloatingIpInitialized(byte[] portMask) implements FloatingIpEvent {
  public FloatingIpInitialized {
    portMask = Objects.requireNonNull(portMask, "portMask").clone();
  }

  @Override public byte[] portMask() { return portMask.clone(); }
}
