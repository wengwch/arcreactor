package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.NetworkSpec;
import java.io.Serializable;
import java.util.Objects;

/** Parent IDs use the SHARED preset; subports use the user network and its LOCAL floating-IP network. */
public record NetworkRequirement(String osParentNetworkId, String osParentSubnetId,
    String osSubNetworkId, String osSubSubnetId, String osSubFloatingNetworkId, int segmentationId, NetworkSpec userNetwork,
    PortBandwidthLimit parentPortBandwidthLimit, PortBandwidthLimit subPortBandwidthLimit) implements Serializable {
  public NetworkRequirement {
    Objects.requireNonNull(parentPortBandwidthLimit, "parentPortBandwidthLimit");
    Objects.requireNonNull(subPortBandwidthLimit, "subPortBandwidthLimit");
    if (userNetwork == null || osParentNetworkId != null || osParentSubnetId != null) {
      requireText(osParentNetworkId, "osParentNetworkId");
      requireText(osParentSubnetId, "osParentSubnetId");
    }
    if (userNetwork == null || osSubNetworkId != null || osSubSubnetId != null || osSubFloatingNetworkId != null) {
      requireText(osSubNetworkId, "osSubNetworkId");
      requireText(osSubSubnetId, "osSubSubnetId");
      requireText(osSubFloatingNetworkId, "osSubFloatingNetworkId");
    }
    if (segmentationId < 1 || segmentationId > 4094) {
      throw new IllegalArgumentException("VLAN segmentationId must be between 1 and 4094");
    }
  }

  /** Both port limits travel with the create command and its persisted instance specification. */
  public NetworkRequirement(int segmentationId, NetworkSpec userNetwork,
      PortBandwidthLimit parentPortBandwidthLimit, PortBandwidthLimit subPortBandwidthLimit) {
    this(null, null, null, null, null, segmentationId, Objects.requireNonNull(userNetwork, "userNetwork"),
        parentPortBandwidthLimit, subPortBandwidthLimit);
  }

  public NetworkRequirement withSubNetwork(String osNetworkId, String osSubnetId, String floatingNetworkId) {
    requireText(osNetworkId, "osSubNetworkId");
    requireText(osSubnetId, "osSubSubnetId");
    requireText(floatingNetworkId, "osSubFloatingNetworkId");
    return new NetworkRequirement(osParentNetworkId, osParentSubnetId, osNetworkId, osSubnetId, floatingNetworkId,
        segmentationId, userNetwork, parentPortBandwidthLimit, subPortBandwidthLimit);
  }

  public boolean resolved() {
    return osParentNetworkId != null && osParentSubnetId != null && osSubNetworkId != null && osSubSubnetId != null
        && osSubFloatingNetworkId != null;
  }

  public NetworkRequirement withParentNetwork(String osNetworkId, String osSubnetId) {
    requireText(osNetworkId, "osParentNetworkId");
    requireText(osSubnetId, "osParentSubnetId");
    return new NetworkRequirement(osNetworkId, osSubnetId, osSubNetworkId, osSubSubnetId, osSubFloatingNetworkId,
        segmentationId, userNetwork, parentPortBandwidthLimit, subPortBandwidthLimit);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
