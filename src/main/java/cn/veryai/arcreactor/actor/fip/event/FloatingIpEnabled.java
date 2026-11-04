package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.util.Objects;

/** Imported reservations and the complete resulting port usage are persisted together. */
public record FloatingIpEnabled(FloatingIpIdentity identity, byte[] portMask, byte[] usedPortMask)
    implements FloatingIpEvent {
  public FloatingIpEnabled {
    Objects.requireNonNull(identity, "identity");
    portMask = Objects.requireNonNull(portMask, "portMask").clone();
    usedPortMask = Objects.requireNonNull(usedPortMask, "usedPortMask").clone();
  }

  @Override public byte[] portMask() { return portMask.clone(); }
  @Override public byte[] usedPortMask() { return usedPortMask.clone(); }
}
