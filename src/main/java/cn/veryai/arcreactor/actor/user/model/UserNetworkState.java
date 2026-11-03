package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

/** Persisted resources and progress of one user's regional network workflow. */
public record UserNetworkState(NetworkSpec spec, String projectId, String localNetworkId,
    String networkId, String subnetId, String routerId, String routerPortId, Phase phase,
    int attempts, long generation, String failureMessage) implements Serializable {
  public enum Phase { CREATING, RECONCILING, READY, FAILED }

  public static UserNetworkState requested(NetworkSpec spec, String projectId) {
    return new UserNetworkState(spec, projectId, null, null, null, null, null,
        Phase.CREATING, 0, 0, null);
  }

  public String regionId() { return spec.regionId(); }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED; }

  public UserNetworkState attempted() {
    return new UserNetworkState(spec, projectId, localNetworkId, networkId, subnetId, routerId,
        routerPortId, phase, attempts + 1, generation + 1, failureMessage);
  }

  public UserNetworkState reconciling() {
    return new UserNetworkState(spec, projectId, localNetworkId, networkId, subnetId, routerId,
        routerPortId, Phase.RECONCILING, 0, generation + 1, null);
  }

  public UserNetworkState failed(String detail) {
    return new UserNetworkState(spec, projectId, localNetworkId, networkId, subnetId, routerId,
        routerPortId, Phase.FAILED, attempts, generation + 1, detail);
  }

  public UserNetworkState completed(String resourceId) {
    if (resourceId == null || resourceId.isBlank()) {
      throw new IllegalStateException("Missing network resource ID");
    }
    if (terminal()) throw new IllegalStateException("Network workflow has finished");
    String local = localNetworkId;
    String network = networkId;
    String subnet = subnetId;
    String router = routerId;
    String port = routerPortId;
    if (local == null) local = resourceId;
    else if (network == null) network = resourceId;
    else if (subnet == null) subnet = resourceId;
    else if (router == null) router = resourceId;
    else if (port == null) port = resourceId;
    else throw new IllegalStateException("All network resources are already confirmed");
    return new UserNetworkState(spec, projectId,
        local, network, subnet, router, port,
        port == null ? Phase.CREATING : Phase.READY, 0, generation + 1, null);
  }
}
