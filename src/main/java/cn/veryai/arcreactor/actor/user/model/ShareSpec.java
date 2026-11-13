package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.util.Objects;
import org.openstack4j.model.manila.Share;

/** Configurable share attributes; naming is derived from the owning user by UserShareNaming.
 * osProjectId may be unresolved in instance requirements; CreateShare requires a resolved ID. */
public record ShareSpec(
    String regionId,
    String osProjectId,
    int size,
    Share.Protocol shareProto,
    String shareType) implements Serializable {
  public ShareSpec {
    requireText(regionId, "regionId");
    if (osProjectId != null) requireText(osProjectId, "osProjectId");
    requireText(shareType, "shareType");
    Objects.requireNonNull(shareProto, "shareProto");
    if (shareProto != Share.Protocol.CEPHFS) {
      throw new IllegalArgumentException("cephx access requires a CEPHFS share");
    }
    if (size <= 0) throw new IllegalArgumentException("size must be positive (GiB)");
  }

  public ShareSpec(String regionId, int size, Share.Protocol shareProto, String shareType) {
    this(regionId, null, size, shareProto, shareType);
  }

  public ShareSpec withOsProjectId(String osProjectId) {
    requireText(osProjectId, "osProjectId");
    return new ShareSpec(regionId, osProjectId, size, shareProto, shareType);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
