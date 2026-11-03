package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

public record UserProjectState(String clusterId, String name, String projectId, Phase phase,
    long generation, String failureMessage) implements Serializable {
  public enum Phase { CREATING, READY, FAILED }

  public static UserProjectState requested(String clusterId, String name) {
    return new UserProjectState(clusterId, name, null, Phase.CREATING, 0, null);
  }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED; }
  public UserProjectState ready(String id) {
    return new UserProjectState(clusterId, name, id, Phase.READY, generation + 1, null);
  }
  public UserProjectState failed(String detail) {
    return new UserProjectState(clusterId, name, projectId, Phase.FAILED, generation + 1, detail);
  }
}
