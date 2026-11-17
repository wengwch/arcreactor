package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;

/** Stable local identity and OpenStack references persisted when enabling one floating-IP actor. */
public record FloatingIpIdentity(String floatingIpId, String regionId, String osFloatingIpId, String floatingIp, String domain,
    String osProjectId) implements Serializable {
  public FloatingIpIdentity {
    for (String value : new String[] {floatingIpId, regionId, osFloatingIpId, floatingIp, osProjectId}) {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("floating IP identity fields are required");
      }
    }
  }

  public String entityId() { return floatingIpId; }
}
