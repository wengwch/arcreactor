package cn.veryai.arcreactor.actor.fip.event;

import java.util.Objects;

/** Enabling a floating IP persists its imported port reservations before forwarding claims are created. */
public record FloatingIpEnabled(byte[] portMask) implements FloatingIpEvent {
  public FloatingIpEnabled {
    portMask = Objects.requireNonNull(portMask, "portMask").clone();
  }

  @Override public byte[] portMask() { return portMask.clone(); }
}
