package cn.veryai.arcreactor.actor.fip.reply;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import java.io.Serializable;

public record ForwardingReply(Status status, ForwardingClaim claim, String detail) implements Serializable {
  public enum Status { IN_PROGRESS, READY, FAILED, DELETED, NEEDS_RECONCILIATION, NOT_FOUND, REJECTED }
  public static ForwardingReply from(ForwardingClaim claim) {
    Status status = claim.paused() ? Status.NEEDS_RECONCILIATION : switch (claim.phase()) {
      case READY -> Status.READY;
      case FAILED -> Status.FAILED;
      case DELETED -> Status.DELETED;
      default -> Status.IN_PROGRESS;
    };
    return new ForwardingReply(status, claim, claim.failure());
  }
}
