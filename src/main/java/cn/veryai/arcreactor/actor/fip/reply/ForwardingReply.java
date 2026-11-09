package cn.veryai.arcreactor.actor.fip.reply;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingOutcome;
import java.io.Serializable;

public record ForwardingReply(Status status, ForwardingClaim claim, String detail) implements Serializable {
  public enum Status { IN_PROGRESS, READY, FAILED, DELETE_FAILED, DELETED, NOT_FOUND, REJECTED }
  public static ForwardingReply from(ForwardingClaim claim) {
    Status status = switch (claim.phase()) {
      case CREATING, DELETING -> Status.IN_PROGRESS;
      case READY -> Status.READY;
      case FAILED -> Status.FAILED;
      case DELETE_FAILED -> Status.DELETE_FAILED;
      case DELETED -> Status.DELETED;
    };
    return new ForwardingReply(status, claim, claim.failure());
  }

  public static ForwardingReply from(ForwardingOutcome outcome) {
    return new ForwardingReply(outcome.phase() == ForwardingClaim.Phase.DELETED ? Status.DELETED : Status.FAILED,
        null, outcome.failure());
  }
}
