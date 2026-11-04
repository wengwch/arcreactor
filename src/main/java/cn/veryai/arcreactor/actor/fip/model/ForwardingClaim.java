package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;

/** Persisted claim data. The floating-IP actor owns all transitions and never switches IPs. */
public record ForwardingClaim(String claimId, String floatingIpId, ForwardingSpec spec, long order,
    Phase phase, ForwardingResources resources, boolean deleteRequested, boolean writeStarted,
    boolean paused, int attempts, long generation, String failure) implements Serializable {
  public enum Phase {
    ALLOCATING, CREATING_TCP, CREATING_UDP, READY, DELETING_TCP, DELETING_UDP, FAILED, DELETED
  }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED || phase == Phase.DELETED; }
  public boolean writing() {
    return phase == Phase.CREATING_TCP || phase == Phase.CREATING_UDP;
  }
  public boolean deleting() { return phase.name().startsWith("DELETING_"); }
}
