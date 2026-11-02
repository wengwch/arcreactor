package cn.veryai.arcreactor.actor.user.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

  /** Read journal events and snapshots written with per-resource phases and a reconciliation flag. */
  @JsonCreator
  public static UserNetworkState restore(
      @JsonProperty("spec") NetworkSpec spec,
      @JsonProperty("projectId") String projectId,
      @JsonProperty("localNetworkId") String localNetworkId,
      @JsonProperty("networkId") String networkId,
      @JsonProperty("subnetId") String subnetId,
      @JsonProperty("routerId") String routerId,
      @JsonProperty("routerPortId") String routerPortId,
      @JsonProperty("phase") String phase,
      @JsonProperty("reconciling") boolean legacyReconciling,
      @JsonProperty("attempts") int attempts,
      @JsonProperty("generation") long generation,
      @JsonProperty("failureMessage") String failureMessage) {
    Phase restored = switch (phase) {
      case "RESOLVING_LOCAL_NETWORK", "CREATING_NETWORK", "CREATING_SUBNET",
          "CREATING_ROUTER", "ATTACHING_INTERFACE" -> legacyReconciling ? Phase.RECONCILING : Phase.CREATING;
      default -> Phase.valueOf(phase);
    };
    return new UserNetworkState(spec, projectId, localNetworkId, networkId, subnetId, routerId,
        routerPortId, restored, attempts, generation, failureMessage);
  }
}
