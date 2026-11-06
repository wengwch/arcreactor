package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.util.Objects;

/** Enables the actor with a persisted identity and complete resulting port usage. */
public record FloatingIpEnabled(FloatingIpIdentity identity, byte[] usedPortMask)
    implements FloatingIpEvent {
  public FloatingIpEnabled {
    Objects.requireNonNull(identity, "identity");
    usedPortMask = Objects.requireNonNull(usedPortMask, "usedPortMask").clone();
  }

  @Override public byte[] usedPortMask() { return usedPortMask.clone(); }
}
