package cn.veryai.arcreactor.actor.fip.reply;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingOutcome;
import java.io.Serializable;

public record ForwardingReply(Status status, ForwardingClaim claim, String detail) implements Serializable {
  public enum Status { IN_PROGRESS, READY, FAILED, DELETED, NEEDS_RECONCILIATION, NOT_FOUND, REJECTED }
  public static ForwardingReply from(ForwardingClaim claim) {
    Status status = claim.paused() ? Status.NEEDS_RECONCILIATION
        : claim.deleteRequested() && claim.phase() != ForwardingClaim.Phase.DELETED ? Status.IN_PROGRESS
        : switch (claim.phase()) {
      case READY -> Status.READY;
      case FAILED -> Status.FAILED;
      case DELETED -> Status.DELETED;
      default -> Status.IN_PROGRESS;
    };
    return new ForwardingReply(status, claim, claim.failure());
  }

  public static ForwardingReply from(ForwardingOutcome outcome) {
    return new ForwardingReply(outcome.phase() == ForwardingClaim.Phase.DELETED ? Status.DELETED : Status.FAILED,
        null, outcome.failure());
  }
}
