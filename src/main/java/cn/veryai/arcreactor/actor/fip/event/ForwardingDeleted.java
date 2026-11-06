package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.time.Instant;
import java.util.Objects;

/** Both protocol rules have been deleted; the released port usage can now be projected. */
public record ForwardingDeleted(FloatingIpIdentity identity, String claimId, boolean enabled, byte[] usedPortMask,
    Instant deletedAt)
    implements FloatingIpEvent {
  public ForwardingDeleted {
    Objects.requireNonNull(identity, "identity");
    usedPortMask = Objects.requireNonNull(usedPortMask, "usedPortMask").clone();
    Objects.requireNonNull(deletedAt, "deletedAt");
  }

  @Override public byte[] usedPortMask() { return usedPortMask.clone(); }
}
