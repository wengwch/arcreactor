package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

public record UserProjectState(String clusterId, String name, String projectId, Phase phase,
    int attempts, long generation, String failureMessage) implements Serializable {
  public enum Phase { CREATING, RECONCILING, GRANTING_ADMIN_ROLE, READY, FAILED }

  public static UserProjectState requested(String clusterId, String name) {
    return new UserProjectState(clusterId, name, null, Phase.CREATING, 0, 0, null);
  }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED; }
  public UserProjectState attempted() {
    return new UserProjectState(clusterId, name, projectId, phase, attempts + 1, generation + 1, failureMessage);
  }
  public UserProjectState reconciling() {
    return new UserProjectState(clusterId, name, projectId, Phase.RECONCILING, 0, generation + 1, null);
  }
  public UserProjectState created(String id) {
    return new UserProjectState(clusterId, name, id, Phase.GRANTING_ADMIN_ROLE, 0, generation + 1, null);
  }
  public UserProjectState ready() {
    return new UserProjectState(clusterId, name, projectId, Phase.READY, 0, generation + 1, null);
  }
  public UserProjectState failed(String detail) {
    return new UserProjectState(clusterId, name, projectId, Phase.FAILED, attempts, generation + 1, detail);
  }
}
