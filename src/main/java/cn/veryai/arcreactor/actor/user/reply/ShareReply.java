package cn.veryai.arcreactor.actor.user.reply;

import cn.veryai.arcreactor.actor.user.model.UserShareState;
import java.io.Serializable;

public record ShareReply(Status status, UserShareState share, String detail) implements Serializable {
  public enum Status {
    IN_PROGRESS, READY, FAILED, REJECTED, NOT_FOUND
  }

  public static ShareReply from(UserShareState share) {
    Status status = switch (share.phase()) {
      case READY -> Status.READY;
      case FAILED -> Status.FAILED;
      default -> Status.IN_PROGRESS;
    };
    return new ShareReply(status, share, share.failureMessage());
  }
}
