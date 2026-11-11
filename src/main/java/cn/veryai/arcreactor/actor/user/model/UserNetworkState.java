package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.time.Instant;

/** Persisted resources and progress of one user's regional network workflow. */
public record UserNetworkState(NetworkSpec spec, String osProjectId, String osLocalNetworkId,
    String osNetworkId, String osSubnetId, String osRouterId, String osRouterPortId, Phase phase,
    long generation, String failureMessage, Instant deadline) implements Serializable {
  public enum Phase { CREATING, READY, FAILED }

  public static UserNetworkState requested(NetworkSpec spec, String osProjectId, Instant deadline) {
    return new UserNetworkState(spec, osProjectId, null, null, null, null, null,
        Phase.CREATING, 0, null, deadline);
  }

  public String regionId() { return spec.regionId(); }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED; }

  public UserNetworkState ready(NetworkResources resources) {
    return new UserNetworkState(spec, osProjectId, resources.osLocalNetworkId(), resources.osNetworkId(),
        resources.osSubnetId(), resources.osRouterId(), resources.osRouterPortId(),
        Phase.READY, generation + 1, null, deadline);
  }

  public UserNetworkState failed(String detail) {
    return new UserNetworkState(spec, osProjectId, osLocalNetworkId, osNetworkId, osSubnetId, osRouterId,
        osRouterPortId, Phase.FAILED, generation + 1, detail, deadline);
  }
}
