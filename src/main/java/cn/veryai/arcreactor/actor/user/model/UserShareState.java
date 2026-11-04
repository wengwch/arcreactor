package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.time.Instant;

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
    String failureMessage,
    Instant deadline) implements Serializable {
  public enum Phase {
    CREATING, WAITING_EXPORT, GRANTING_ACCESS, WAITING_ACCESS, READY, FAILED
  }

  public static UserShareState requested(String shareId, ShareSpec spec, Instant deadline) {
    return new UserShareState(shareId, spec, Phase.CREATING, null, null, null, null, null, 0, null, deadline);
  }

  public String regionId() {
    return spec.regionId();
  }

  public boolean terminal() {
    return phase == Phase.READY || phase == Phase.FAILED;
  }

  public UserShareState created(String id) {
    requireText(id, "osShareId");
    return new UserShareState(shareId, spec, Phase.WAITING_EXPORT, id, null, null, null, null,
        generation + 1, null, deadline);
  }

  public UserShareState exportReady(String id, String path) {
    requireText(id, "osExportLocationId");
    requireText(path, "exportLocationPath");
    return new UserShareState(shareId, spec, Phase.GRANTING_ACCESS, osShareId, id, path, null, null,
        generation + 1, null, deadline);
  }

  public UserShareState accessCreated(String id) {
    requireText(id, "osGrantAccessId");
    return new UserShareState(shareId, spec, Phase.WAITING_ACCESS, osShareId, osExportLocationId,
        exportLocationPath, id, null, generation + 1, null, deadline);
  }

  public UserShareState ready(String key) {
    requireText(osShareId, "osShareId");
    requireText(osExportLocationId, "osExportLocationId");
    requireText(exportLocationPath, "exportLocationPath");
    requireText(osGrantAccessId, "osGrantAccessId");
    requireText(key, "accessKey");
    return new UserShareState(shareId, spec, Phase.READY, osShareId, osExportLocationId,
        exportLocationPath, osGrantAccessId, key, generation + 1, null, deadline);
  }

  public UserShareState failed(String message) {
    return new UserShareState(shareId, spec, Phase.FAILED, osShareId, osExportLocationId,
        exportLocationPath, osGrantAccessId, null, generation + 1, message, deadline);
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
