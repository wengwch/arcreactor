package cn.veryai.arcreactor.actor.user.reply;

import cn.veryai.arcreactor.actor.user.model.UserCleanupState;
import java.io.Serializable;

public record UserCleanupReply(Status status, String userId, UserCleanupState cleanup,
    String currentResourceId, int remainingNetworks, int remainingShares, int remainingProjects,
    String detail) implements Serializable {
  public enum Status { ACCEPTED, REJECTED, NOT_STARTED }
}
