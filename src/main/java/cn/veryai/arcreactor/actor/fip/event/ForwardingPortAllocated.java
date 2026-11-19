package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpState.Status;
import java.util.Objects;

/** Reserves the shared external TCP/UDP port before any forwarding rule is created. */
public record ForwardingPortAllocated(FloatingIpIdentity identity, String claimId, int externalPort,
    Status status, byte[] usedPortMask)
    implements FloatingIpEvent {
  public ForwardingPortAllocated {
    Objects.requireNonNull(identity, "identity");
    usedPortMask = Objects.requireNonNull(usedPortMask, "usedPortMask").clone();
  }

  @Override public byte[] usedPortMask() { return usedPortMask.clone(); }
}
