package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;

/** The untagged parent network and the VLAN network carried by its trunk. */
public record NetworkRequirement(String parentNetworkId, String parentSubnetId,
    String subNetworkId, String subSubnetId, int segmentationId) implements Serializable {
  public NetworkRequirement {
    requireText(parentNetworkId, "parentNetworkId");
    requireText(parentSubnetId, "parentSubnetId");
    requireText(subNetworkId, "subNetworkId");
    requireText(subSubnetId, "subSubnetId");
    if (segmentationId < 1 || segmentationId > 4094) {
      throw new IllegalArgumentException("VLAN segmentationId must be between 1 and 4094");
    }
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
