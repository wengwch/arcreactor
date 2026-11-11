package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

/** Accumulated resources owned by this instance workflow. */
public record PreparedNetwork(String osParentPortId, String parentMacAddress, String parentFixedIp,
    String osSubPortId, String subFixedIp, String osSubFloatingIpId, String subFloatingIpAddress,
    String osTrunkId, PreparedPortQos parentQos, PreparedPortQos subQos) implements Serializable {
  public static PreparedNetwork empty() {
    return new PreparedNetwork(null, null, null, null, null, null, null, null, null, null);
  }

  public static PreparedNetwork parentPort(String osPortId, String macAddress, String fixedIp, PreparedPortQos qos) {
    return new PreparedNetwork(osPortId, macAddress, fixedIp, null, null, null, null, null, qos, null);
  }

  public PreparedNetwork withSubPort(String osPortId, String fixedIp, String osFloatingIpId,
      String floatingIpAddress, PreparedPortQos qos) {
    return new PreparedNetwork(osParentPortId, parentMacAddress, parentFixedIp, osPortId, fixedIp,
        osFloatingIpId, floatingIpAddress, osTrunkId, parentQos, qos);
  }

  public PreparedNetwork withTrunk(String osTrunkId) {
    return new PreparedNetwork(osParentPortId, parentMacAddress, parentFixedIp, osSubPortId, subFixedIp,
        osSubFloatingIpId, subFloatingIpAddress, osTrunkId, parentQos, subQos);
  }

  /** The subport is created and reconciled with the parent's MAC address. */
  public String subMacAddress() {
    return osSubPortId == null ? null : parentMacAddress;
  }
}
