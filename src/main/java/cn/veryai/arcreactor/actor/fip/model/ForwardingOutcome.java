package cn.veryai.arcreactor.actor.fip.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim.Phase;
import java.io.Serializable;
import java.time.Instant;

/** Compact result for a claim that no longer owns any remote resources. */
public record ForwardingOutcome(Phase phase, String failure, Instant completedAt) implements Serializable {
  public static ForwardingOutcome from(ForwardingClaim claim, Instant completedAt) {
    return new ForwardingOutcome(claim.phase(), claim.failure(), completedAt);
  }

  /** Records deletion before any create was accepted, blocking delayed creates during retention. */
  public static ForwardingOutcome tombstone(Instant deletedAt) {
    return new ForwardingOutcome(Phase.DELETED, null, deletedAt);
  }

  public ForwardingOutcome deleted(Instant deletedAt) {
    return phase == Phase.DELETED ? this
        : new ForwardingOutcome(Phase.DELETED, failure, deletedAt);
  }
}
