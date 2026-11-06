package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.time.Instant;
import java.util.Objects;

/** Records deletion after cleanup or for an absent claim, carrying the resulting port usage. */
public record ForwardingDeleted(FloatingIpIdentity identity, String claimId, boolean enabled, byte[] usedPortMask,
    Instant deletedAt)
    implements FloatingIpEvent {
  public ForwardingDeleted {
    Objects.requireNonNull(identity, "identity");
    if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
    usedPortMask = Objects.requireNonNull(usedPortMask, "usedPortMask").clone();
    Objects.requireNonNull(deletedAt, "deletedAt");
  }

  @Override public byte[] usedPortMask() { return usedPortMask.clone(); }
}
