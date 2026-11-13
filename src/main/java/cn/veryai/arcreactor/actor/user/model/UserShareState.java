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
    long generation,
    String failureMessage) implements Serializable {
  public enum Phase {
    CREATING, READY, FAILED
  }

  public static UserShareState requested(String shareId, ShareSpec spec) {
    return new UserShareState(shareId, spec, Phase.CREATING, null, null, null, null, null, 0, null);
  }

  public String regionId() {
    return spec.regionId();
  }

  public boolean terminal() {
    return phase == Phase.READY || phase == Phase.FAILED;
  }

  public UserShareState retry() {
    return new UserShareState(shareId, spec, Phase.CREATING, osShareId, osExportLocationId,
        exportLocationPath, osGrantAccessId, null,
        generation + 1, null);
  }

  public boolean hasShare() {
    return osShareId != null;
  }

  public boolean hasExportLocation() {
    return osExportLocationId != null && exportLocationPath != null;
  }

  public boolean hasAccessRule() {
    return osGrantAccessId != null;
  }

  public UserShareState created(String id) {
    requireText(id, "osShareId");
    return new UserShareState(shareId, spec, Phase.CREATING, id, null, null, null, null,
        generation + 1, null);
  }

  public UserShareState exportReady(String id, String path) {
    requireText(id, "osExportLocationId");
    requireText(path, "exportLocationPath");
    return new UserShareState(shareId, spec, Phase.CREATING, osShareId, id, path, null, null,
        generation + 1, null);
  }

  public UserShareState accessCreated(String id) {
    requireText(id, "osGrantAccessId");
    return new UserShareState(shareId, spec, Phase.CREATING, osShareId, osExportLocationId,
        exportLocationPath, id, null, generation + 1, null);
  }

  public UserShareState ready(String key) {
    requireText(osShareId, "osShareId");
    requireText(osExportLocationId, "osExportLocationId");
    requireText(exportLocationPath, "exportLocationPath");
    requireText(osGrantAccessId, "osGrantAccessId");
    requireText(key, "accessKey");
    return new UserShareState(shareId, spec, Phase.READY, osShareId, osExportLocationId,
        exportLocationPath, osGrantAccessId, key, generation + 1, null);
  }

  public UserShareState failed(String message) {
    return new UserShareState(shareId, spec, Phase.FAILED, osShareId, osExportLocationId,
        exportLocationPath, osGrantAccessId, null, generation + 1, message);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }

  @Override
  public String toString() {
    return "UserShareState[shareId=" + shareId + ", phase=" + phase + ", osShareId="
        + osShareId + ", generation=" + generation + "]";
  }
}
