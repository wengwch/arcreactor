package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

/** Accumulated resources owned by this instance workflow. */
public record PreparedNetwork(String parentPortId, String parentMacAddress, String parentFixedIp,
    String subPortId, String subFixedIp, String trunkId) implements Serializable {
  public static PreparedNetwork empty() {
    return new PreparedNetwork(null, null, null, null, null, null);
  }

  /** The subport is created and reconciled with the parent's MAC address. */
  public String subMacAddress() {
    return subPortId == null ? null : parentMacAddress;
  }
}
