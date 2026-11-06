package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/** Removes expired terminal results without releasing ports. */
public record ForwardingOutcomesCleaned(FloatingIpIdentity identity, Set<String> claimIds,
    Instant cleanedAt) implements FloatingIpEvent {
  public ForwardingOutcomesCleaned {
    Objects.requireNonNull(identity, "identity");
    claimIds = Set.copyOf(claimIds);
    Objects.requireNonNull(cleanedAt, "cleanedAt");
  }
}
