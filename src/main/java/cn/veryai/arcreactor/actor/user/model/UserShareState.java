package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

public record UserShareState(
    String shareId,
    ShareSpec spec,
    Phase phase,
    String osShareId,
    String osExportLocationId,
    String exportLocationPath,
    String osGrantAccessId,
    String accessKey,
    int attempts,
    long generation,
    String failureMessage) implements Serializable {
  public enum Phase {
    CREATING, RECONCILING_CREATE, WAITING_SHARE, GRANTING_ACCESS,
    RECONCILING_ACCESS, WAITING_ACCESS, READY, FAILED
  }

  public static UserShareState requested(String shareId, ShareSpec spec) {
    return new UserShareState(shareId, spec, Phase.CREATING, null, null, null, null, null, 0, 0, null);
  }

  public String regionId() {
    return spec.regionId();
  }

  public boolean terminal() {
    return phase == Phase.READY || phase == Phase.FAILED;
  }

  public UserShareState attempted() {
    return new UserShareState(shareId, spec, phase, osShareId, osExportLocationId,
        exportLocationPath, osGrantAccessId, accessKey, attempts + 1, generation + 1, failureMessage);
  }

  public UserShareState transition(Phase nextPhase, String nextShareId, String exportId,
      String exportPath, String accessId, String key, String failure) {
    return new UserShareState(shareId, spec, nextPhase, nextShareId, exportId, exportPath,
        accessId, key, nextPhase == phase ? attempts : 0, generation + 1, failure);
  }

  public UserShareState phase(Phase nextPhase) {
    return transition(nextPhase, osShareId, osExportLocationId, exportLocationPath,
        osGrantAccessId, accessKey, null);
  }

  public UserShareState failed(String message) {
    return transition(Phase.FAILED, osShareId, osExportLocationId, exportLocationPath,
        osGrantAccessId, null, message);
  }

  @Override
  public String toString() {
    return "UserShareState[shareId=" + shareId + ", phase=" + phase + ", osShareId="
        + osShareId + ", attempts=" + attempts + ", generation=" + generation + "]";
  }
}
