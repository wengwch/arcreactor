package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.NetworkSpec;
import java.io.Serializable;
import java.util.Objects;

/** Parent IDs are resolved from the regional SHARED preset; subport IDs come from the user's network. */
public record NetworkRequirement(String parentNetworkId, String parentSubnetId,
    String subNetworkId, String subSubnetId, int segmentationId, NetworkSpec userNetwork,
    PortBandwidthLimit parentPortBandwidthLimit, PortBandwidthLimit subPortBandwidthLimit) implements Serializable {
  public NetworkRequirement {
    Objects.requireNonNull(parentPortBandwidthLimit, "parentPortBandwidthLimit");
    Objects.requireNonNull(subPortBandwidthLimit, "subPortBandwidthLimit");
    if (userNetwork == null || parentNetworkId != null || parentSubnetId != null) {
      requireText(parentNetworkId, "parentNetworkId");
      requireText(parentSubnetId, "parentSubnetId");
    }
    if (userNetwork == null || subNetworkId != null || subSubnetId != null) {
      requireText(subNetworkId, "subNetworkId");
      requireText(subSubnetId, "subSubnetId");
    }
    if (segmentationId < 1 || segmentationId > 4094) {
      throw new IllegalArgumentException("VLAN segmentationId must be between 1 and 4094");
    }
  }

  public NetworkRequirement(String parentNetworkId, String parentSubnetId,
      String subNetworkId, String subSubnetId, int segmentationId, NetworkSpec userNetwork) {
    this(parentNetworkId, parentSubnetId, subNetworkId, subSubnetId, segmentationId, userNetwork,
        PortBandwidthLimit.UNLIMITED, PortBandwidthLimit.UNLIMITED);
  }

  /** Network IDs are resolved by the instance workflow before creating either port. */
  public NetworkRequirement(int segmentationId, NetworkSpec userNetwork) {
    this(segmentationId, userNetwork, PortBandwidthLimit.UNLIMITED, PortBandwidthLimit.UNLIMITED);
  }

  /** Both port limits travel with the create command and its persisted instance specification. */
  public NetworkRequirement(int segmentationId, NetworkSpec userNetwork,
      PortBandwidthLimit parentPortBandwidthLimit, PortBandwidthLimit subPortBandwidthLimit) {
    this(null, null, null, null, segmentationId, Objects.requireNonNull(userNetwork, "userNetwork"),
        parentPortBandwidthLimit, subPortBandwidthLimit);
  }

  /** Retained for callers supplying parent IDs; new workflows resolve the parent from the SHARED preset. */
  public NetworkRequirement(String parentNetworkId, String parentSubnetId,
      int segmentationId, NetworkSpec userNetwork) {
    this(parentNetworkId, parentSubnetId, null, null, segmentationId,
        Objects.requireNonNull(userNetwork, "userNetwork"));
  }

  /** Retained for replaying workflows that supplied subnet IDs before user network preparation. */
  public NetworkRequirement(String parentNetworkId, String parentSubnetId,
      String subNetworkId, String subSubnetId, int segmentationId) {
    this(parentNetworkId, parentSubnetId, subNetworkId, subSubnetId, segmentationId, null);
  }

  public NetworkRequirement withSubNetwork(String networkId, String subnetId) {
    requireText(networkId, "subNetworkId");
    requireText(subnetId, "subSubnetId");
    return new NetworkRequirement(parentNetworkId, parentSubnetId, networkId, subnetId, segmentationId, userNetwork,
        parentPortBandwidthLimit, subPortBandwidthLimit);
  }

  public NetworkRequirement withParentNetwork(String networkId, String subnetId) {
    requireText(networkId, "parentNetworkId");
    requireText(subnetId, "parentSubnetId");
    return new NetworkRequirement(networkId, subnetId, subNetworkId, subSubnetId, segmentationId, userNetwork,
        parentPortBandwidthLimit, subPortBandwidthLimit);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
