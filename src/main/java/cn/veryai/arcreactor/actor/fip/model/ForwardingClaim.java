package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;

public record ForwardingClaim(String claimId, ForwardingSpec spec, long order, Phase phase,
    int candidateIndex, ForwardingResources resources, boolean deleteRequested,
    boolean retryNextCandidate, boolean writeStarted, boolean paused, int attempts,
    long generation, String failure) implements Serializable {
  public enum Phase {
    ALLOCATING, CREATING_TCP, CREATING_UDP, ENSURING_TCP_RULE, ENSURING_UDP_RULE,
    READY, DELETING_TCP, DELETING_UDP, DELETING_TCP_RULE, DELETING_UDP_RULE,
    FAILED, DELETED
  }

  public static ForwardingClaim requested(String id, ForwardingSpec spec, long order) {
    return new ForwardingClaim(id, spec, order, Phase.ALLOCATING, 0, ForwardingResources.empty(),
        false, false, false, false, 0, 0, null);
  }

  public String floatingIpId() { return spec.floatingIpIds().get(candidateIndex); }
  public boolean terminal() { return phase == Phase.READY || phase == Phase.FAILED || phase == Phase.DELETED; }
  public boolean writing() {
    return phase == Phase.CREATING_TCP || phase == Phase.CREATING_UDP
        || phase == Phase.ENSURING_TCP_RULE || phase == Phase.ENSURING_UDP_RULE;
  }
  public boolean deleting() { return phase.name().startsWith("DELETING_"); }

  public ForwardingClaim attempted() {
    return copy(phase, candidateIndex, resources, deleteRequested, retryNextCandidate,
        writeStarted || writing(), false, attempts + 1, failure);
  }

  public ForwardingClaim advance(Phase next, ForwardingResources nextResources) {
    return copy(next, candidateIndex, nextResources, deleteRequested, retryNextCandidate,
        false, false, 0, next == Phase.READY ? null : failure);
  }

  public ForwardingClaim cleanup(boolean retry, String detail) {
    return copy(Phase.DELETING_TCP, candidateIndex, resources, deleteRequested, retry,
        false, false, 0, detail);
  }

  public ForwardingClaim requestDelete() {
    // An uncertain write must be reconciled before its port can be freed.
    Phase next = writing() && writeStarted ? phase : (deleting() ? phase : Phase.DELETING_TCP);
    return copy(next, candidateIndex, resources, true, false,
        next == phase && writeStarted, false, 0, failure);
  }

  public ForwardingClaim pause(String detail) {
    return copy(phase, candidateIndex, resources, deleteRequested, retryNextCandidate,
        writeStarted, true, attempts, detail);
  }

  public ForwardingClaim resume() {
    return copy(phase, candidateIndex, resources, deleteRequested, retryNextCandidate,
        writeStarted, false, 0, failure);
  }

  public ForwardingClaim nextCandidate() {
    return copy(Phase.ALLOCATING, candidateIndex + 1, ForwardingResources.empty(),
        false, false, false, false, 0, failure);
  }

  private ForwardingClaim copy(Phase next, int index, ForwardingResources nextResources,
      boolean deleting, boolean retry, boolean written, boolean suspended, int nextAttempts, String detail) {
    return new ForwardingClaim(claimId, spec, order, next, index, nextResources, deleting,
        retry, written, suspended, nextAttempts, generation + 1, detail);
  }
}
