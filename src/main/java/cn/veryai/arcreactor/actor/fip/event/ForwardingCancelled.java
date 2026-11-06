package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.time.Instant;
import java.util.Objects;

/** A claim tombstone prevents delayed create requests from resurrecting a deleted mapping. */
public record ForwardingCancelled(FloatingIpIdentity identity, String claimId, Instant cancelledAt)
    implements FloatingIpEvent {
  public ForwardingCancelled {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(cancelledAt, "cancelledAt");
    if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
  }
}
