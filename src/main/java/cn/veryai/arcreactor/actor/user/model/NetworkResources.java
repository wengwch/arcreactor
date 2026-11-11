package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

/** All OpenStack resource IDs required for a ready user network. */
public record NetworkResources(String osLocalNetworkId, String osNetworkId, String osSubnetId,
    String osRouterId, String osRouterPortId) implements Serializable {
  public NetworkResources {
    requireId(osLocalNetworkId, "osLocalNetworkId");
    requireId(osNetworkId, "osNetworkId");
    requireId(osSubnetId, "osSubnetId");
    requireId(osRouterId, "osRouterId");
    requireId(osRouterPortId, "osRouterPortId");
  }

  private static void requireId(String id, String name) {
    if (id == null || id.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
