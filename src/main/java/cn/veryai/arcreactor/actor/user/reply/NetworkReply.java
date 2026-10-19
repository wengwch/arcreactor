package cn.veryai.arcreactor.actor.user.reply;

import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import java.io.Serializable;

public record NetworkReply(Status status, String userId, String regionId, UserNetworkState network,
    String detail) implements Serializable {
  public enum Status { IN_PROGRESS, READY, FAILED, REJECTED, NOT_FOUND }

  public static NetworkReply from(String userId, UserNetworkState network) {
    Status status = switch (network.phase()) {
      case READY -> Status.READY;
      case FAILED -> Status.FAILED;
      default -> Status.IN_PROGRESS;
    };
    return new NetworkReply(status, userId, network.regionId(), network, network.failureMessage());
  }
}
