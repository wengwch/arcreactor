package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.time.Instant;

/** Persisted resources and progress of one user's regional network workflow. */
public record UserNetworkState(NetworkSpec spec, String projectId, String localNetworkId,
    String networkId, String subnetId, String routerId, String routerPortId, Phase phase,
    long generation, String failureMessage, Instant deadline) implements Serializable {
  public enum Phase { CREATING, READY, FAILED }

  public static UserNetworkState requested(NetworkSpec spec, String projectId, Instant deadline) {
    return new UserNetworkState(spec, projectId, null, null, null, null, null,
        Phase.CREATING, 0, null, deadline);
  }

  public String regionId() { return spec.regionId(); }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED; }

  public UserNetworkState ready(NetworkResources resources) {
    return new UserNetworkState(spec, projectId, resources.localNetworkId(), resources.networkId(),
        resources.subnetId(), resources.routerId(), resources.routerPortId(),
        Phase.READY, generation + 1, null, deadline);
  }

  public UserNetworkState failed(String detail) {
    return new UserNetworkState(spec, projectId, localNetworkId, networkId, subnetId, routerId,
        routerPortId, Phase.FAILED, generation + 1, detail, deadline);
  }
}
