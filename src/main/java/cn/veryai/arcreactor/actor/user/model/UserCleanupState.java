package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

/** Durable cleanup progress; resources remain in UserState until deletion is confirmed. */
public record UserCleanupState(Phase phase, long generation, String resourceId,
    String failureMessage) implements Serializable {
  public enum Phase { NETWORKS, SHARES, PROJECTS, COMPLETED, FAILED }

  public static UserCleanupState started(long generation) {
    return new UserCleanupState(Phase.NETWORKS, generation, null, null);
  }

  public boolean active() {
    return phase != Phase.COMPLETED && phase != Phase.FAILED;
  }

  public UserCleanupState advance(Phase next) {
    return new UserCleanupState(next, generation, null, null);
  }

  public UserCleanupState failed(String resourceId, String failure) {
    return new UserCleanupState(Phase.FAILED, generation, resourceId, failure);
  }
}
