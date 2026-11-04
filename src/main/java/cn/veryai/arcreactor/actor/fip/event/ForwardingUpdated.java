package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import java.util.Objects;

/** The port mask is the complete actor usage after applying this claim, including imported reservations. */
public record ForwardingUpdated(FloatingIpIdentity identity, ForwardingClaim claim, byte[] usedPortMask)
    implements FloatingIpEvent {
  public ForwardingUpdated {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(claim, "claim");
    usedPortMask = Objects.requireNonNull(usedPortMask, "usedPortMask").clone();
  }

  @Override public byte[] usedPortMask() { return usedPortMask.clone(); }
}
