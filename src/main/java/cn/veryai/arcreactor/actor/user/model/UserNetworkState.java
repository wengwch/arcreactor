package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

/** Persisted network data; UserActor owns the state transitions. */
public record UserNetworkState(NetworkSpec spec, String projectId, String localNetworkId,
    String networkId, String subnetId, String routerId, String routerPortId, Phase phase,
    boolean reconciling, int attempts, long generation, String failureMessage) implements Serializable {
  public enum Phase { RESOLVING_LOCAL_NETWORK, CREATING_NETWORK, CREATING_SUBNET,
    CREATING_ROUTER, ATTACHING_INTERFACE, READY, FAILED }

  public String regionId() { return spec.regionId(); }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED; }
  public boolean writesResource() { return !terminal() && phase != Phase.RESOLVING_LOCAL_NETWORK; }
}
