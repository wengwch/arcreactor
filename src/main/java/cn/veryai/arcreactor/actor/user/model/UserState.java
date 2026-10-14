package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public record UserState(Map<String, UserShareState> shares) implements Serializable {
  public UserState {
    shares = Map.copyOf(shares);
  }

  public UserState updated(UserShareState share) {
    Map<String, UserShareState> next = new HashMap<>(shares);
    next.put(share.shareId(), share);
    return new UserState(next);
  }
}
