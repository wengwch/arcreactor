package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

public record UserProjectState(String clusterId, String name, String osProjectId, Phase phase,
    long generation, String failureMessage) implements Serializable {
  public enum Phase { CREATING, READY, FAILED }

  public static UserProjectState requested(String clusterId, String name) {
    return new UserProjectState(clusterId, name, null, Phase.CREATING, 0, null);
  }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED; }
  public UserProjectState retry() {
    return new UserProjectState(clusterId, name, null, Phase.CREATING, generation + 1, null);
  }
  public UserProjectState ready(String osProjectId) {
    return new UserProjectState(clusterId, name, osProjectId, Phase.READY, generation + 1, null);
  }
  public UserProjectState failed(String detail) {
    return new UserProjectState(clusterId, name, osProjectId, Phase.FAILED, generation + 1, detail);
  }
}
