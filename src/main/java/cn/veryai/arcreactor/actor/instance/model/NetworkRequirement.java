package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.NetworkSpec;
import java.io.Serializable;
import java.util.Objects;

/** Parent IDs use the SHARED preset; subports use the user network and its LOCAL floating-IP network. */
public record NetworkRequirement(String parentNetworkId, String parentSubnetId,
    String subNetworkId, String subSubnetId, String subFloatingNetworkId, int segmentationId, NetworkSpec userNetwork,
    PortBandwidthLimit parentPortBandwidthLimit, PortBandwidthLimit subPortBandwidthLimit) implements Serializable {
  public NetworkRequirement {
    Objects.requireNonNull(parentPortBandwidthLimit, "parentPortBandwidthLimit");
    Objects.requireNonNull(subPortBandwidthLimit, "subPortBandwidthLimit");
    if (userNetwork == null || parentNetworkId != null || parentSubnetId != null) {
      requireText(parentNetworkId, "parentNetworkId");
      requireText(parentSubnetId, "parentSubnetId");
    }
    if (userNetwork == null || subNetworkId != null || subSubnetId != null || subFloatingNetworkId != null) {
      requireText(subNetworkId, "subNetworkId");
      requireText(subSubnetId, "subSubnetId");
      requireText(subFloatingNetworkId, "subFloatingNetworkId");
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

  public NetworkRequirement withSubNetwork(String networkId, String subnetId, String floatingNetworkId) {
    requireText(networkId, "subNetworkId");
    requireText(subnetId, "subSubnetId");
    requireText(floatingNetworkId, "subFloatingNetworkId");
    return new NetworkRequirement(parentNetworkId, parentSubnetId, networkId, subnetId, floatingNetworkId,
        segmentationId, userNetwork, parentPortBandwidthLimit, subPortBandwidthLimit);
  }

  public boolean resolved() {
    return parentNetworkId != null && parentSubnetId != null && subNetworkId != null && subSubnetId != null
        && subFloatingNetworkId != null;
  }

  public NetworkRequirement withParentNetwork(String networkId, String subnetId) {
    requireText(networkId, "parentNetworkId");
    requireText(subnetId, "parentSubnetId");
    return new NetworkRequirement(networkId, subnetId, subNetworkId, subSubnetId, subFloatingNetworkId,
        segmentationId, userNetwork, parentPortBandwidthLimit, subPortBandwidthLimit);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
