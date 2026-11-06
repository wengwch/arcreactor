package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.util.Objects;

/** Stops accepting new claims while retaining all current port reservations. */
public record FloatingIpDisabled(FloatingIpIdentity identity, byte[] usedPortMask) implements FloatingIpEvent {
  public FloatingIpDisabled {
    Objects.requireNonNull(identity, "identity");
    usedPortMask = Objects.requireNonNull(usedPortMask, "usedPortMask").clone();
  }

  @Override public byte[] usedPortMask() { return usedPortMask.clone(); }
}
