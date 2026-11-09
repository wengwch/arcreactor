package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.time.Instant;
import java.util.Objects;

/** Creation failed; allocated ports remain reserved until explicit deletion confirms cleanup. */
public record ForwardingCreationFailed(FloatingIpIdentity identity, String claimId, String failure,
    Instant failedAt) implements FloatingIpEvent {
  public ForwardingCreationFailed {
    Objects.requireNonNull(failedAt, "failedAt");
  }
}
