package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;
import java.util.List;

/** Immutable OpenStack identity persisted when enabling one floating-IP actor. */
public record FloatingIpIdentity(String regionId, String osFloatingIpId, String floatingIp,
    String osProjectId) implements Serializable {
  public FloatingIpIdentity {
    for (String value : List.of(regionId, osFloatingIpId, floatingIp, osProjectId)) {
      if (value.isBlank()) throw new IllegalArgumentException("floating IP identity fields are required");
    }
  }

  public String entityId() { return entityId(regionId, osFloatingIpId); }

  public static String entityId(String regionId, String osFloatingIpId) {
    if (regionId == null || regionId.isBlank() || osFloatingIpId == null || osFloatingIpId.isBlank()) {
      throw new IllegalArgumentException("region and osFloatingIpId are required");
    }
    return regionId.length() + ":" + regionId + osFloatingIpId;
  }
}
