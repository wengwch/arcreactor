package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.util.Objects;
import org.openstack4j.model.manila.Share;

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
    requireText(osProjectId, "osProjectId");
    requireText(name, "name");
    requireText(shareType, "shareType");
    requireText(accessTo, "accessTo");
    Objects.requireNonNull(shareProto, "shareProto");
    if (shareProto != Share.Protocol.CEPHFS) {
      throw new IllegalArgumentException("cephx access requires a CEPHFS share");
    }
    if (size <= 0) throw new IllegalArgumentException("size must be positive (GiB)");
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
