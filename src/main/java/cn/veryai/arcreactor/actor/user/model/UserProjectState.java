package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.time.Instant;

public record UserProjectState(String clusterId, String name, String osProjectId, Phase phase,
    long generation, String failureMessage, Instant deadline) implements Serializable {
  public enum Phase { CREATING, READY, FAILED }

  public static UserProjectState requested(String clusterId, String name, Instant deadline) {
    return new UserProjectState(clusterId, name, null, Phase.CREATING, 0, null, deadline);
  }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED; }
  public UserProjectState ready(String osProjectId) {
    return new UserProjectState(clusterId, name, osProjectId, Phase.READY, generation + 1, null, deadline);
  }
  public UserProjectState failed(String detail) {
    return new UserProjectState(clusterId, name, osProjectId, Phase.FAILED, generation + 1, detail, deadline);
  }
}
