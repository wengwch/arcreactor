package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;
import java.util.List;

/**
 * Immutable OpenStack identity passed when constructing one floating-IP actor.
 * The per-IP persistence key differs from the old regional key; regional journals require migration.
 */
public record FloatingIpIdentity(String regionId, String floatingIpId, String floatingIp,
    String projectId) implements Serializable {
  public FloatingIpIdentity {
    for (String value : List.of(regionId, floatingIpId, floatingIp, projectId)) {
      if (value.isBlank()) throw new IllegalArgumentException("floating IP identity fields are required");
    }
  }

  public String entityId() { return entityId(regionId, floatingIpId); }

  public static String entityId(String regionId, String floatingIpId) {
    if (regionId == null || regionId.isBlank() || floatingIpId == null || floatingIpId.isBlank()) {
      throw new IllegalArgumentException("region and floatingIpId are required");
    }
    return regionId.length() + ":" + regionId + floatingIpId;
  }
}
