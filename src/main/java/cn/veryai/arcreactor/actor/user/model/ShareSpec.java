package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.util.Objects;
import org.openstack4j.model.manila.Share;

/** osProjectId may be unresolved during API dependency preparation; CreateShare requires a resolved ID. */
public record ShareSpec(
    String regionId,
    String osProjectId,
    String name,
    String description,
    int size,
    Share.Protocol shareProto,
    String shareType,
    String accessTo) implements Serializable {
  public ShareSpec {
    requireText(regionId, "regionId");
    if (osProjectId != null) requireText(osProjectId, "osProjectId");
    requireText(name, "name");
    requireText(shareType, "shareType");
    requireText(accessTo, "accessTo");
    Objects.requireNonNull(shareProto, "shareProto");
    if (shareProto != Share.Protocol.CEPHFS) {
      throw new IllegalArgumentException("cephx access requires a CEPHFS share");
    }
    if (size <= 0) throw new IllegalArgumentException("size must be positive (GiB)");
  }

  public ShareSpec(String regionId, String name, String description, int size,
      Share.Protocol shareProto, String shareType, String accessTo) {
    this(regionId, null, name, description, size, shareProto, shareType, accessTo);
  }

  public ShareSpec withProjectId(String projectId) {
    requireText(projectId, "osProjectId");
    return new ShareSpec(regionId, projectId, name, description, size, shareProto, shareType, accessTo);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
