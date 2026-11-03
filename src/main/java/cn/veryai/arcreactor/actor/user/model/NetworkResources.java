package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

/** All OpenStack resource IDs required for a ready user network. */
public record NetworkResources(String localNetworkId, String networkId, String subnetId,
    String routerId, String routerPortId) implements Serializable {
  public NetworkResources {
    requireId(localNetworkId, "localNetworkId");
    requireId(networkId, "networkId");
    requireId(subnetId, "subnetId");
    requireId(routerId, "routerId");
    requireId(routerPortId, "routerPortId");
  }

  private static void requireId(String id, String name) {
    if (id == null || id.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
