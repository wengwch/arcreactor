package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

/** Persisted resources and progress of one user's regional network workflow. */
public record UserNetworkState(String clusterId, NetworkSpec spec, String osProjectId, String osLocalNetworkId,
    String osNetworkId, String osSubnetId, String osRouterId, String osRouterPortId, Phase phase,
    long generation, String failureMessage) implements Serializable {
  public enum Phase { CREATING, READY, FAILED }

  public static UserNetworkState requested(String clusterId, NetworkSpec spec, String osProjectId) {
    return new UserNetworkState(clusterId, spec, osProjectId, null, null, null, null, null,
        Phase.CREATING, 0, null);
  }

  public String regionId() { return spec.regionId(); }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED; }

  public UserNetworkState retry() {
    return new UserNetworkState(clusterId, spec, osProjectId, null, null, null, null, null,
        Phase.CREATING, generation + 1, null);
  }

  public UserNetworkState ready(NetworkResources resources) {
    return new UserNetworkState(clusterId, spec, osProjectId, resources.osLocalNetworkId(), resources.osNetworkId(),
        resources.osSubnetId(), resources.osRouterId(), resources.osRouterPortId(),
        Phase.READY, generation + 1, null);
  }

  public UserNetworkState failed(String detail) {
    return new UserNetworkState(clusterId, spec, osProjectId, osLocalNetworkId, osNetworkId, osSubnetId, osRouterId,
        osRouterPortId, Phase.FAILED, generation + 1, detail);
  }
}
