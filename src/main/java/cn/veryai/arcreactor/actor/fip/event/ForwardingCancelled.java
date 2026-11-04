package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.util.Objects;

/** Tombstones prevent delayed create requests from resurrecting deleted instance mappings. */
public record ForwardingCancelled(FloatingIpIdentity identity, String claimId, String instanceId)
    implements FloatingIpEvent {
  public ForwardingCancelled {
    Objects.requireNonNull(identity, "identity");
    if (claimId == null && instanceId == null) {
      throw new IllegalArgumentException("a claim or instance cancellation target is required");
    }
  }
}
