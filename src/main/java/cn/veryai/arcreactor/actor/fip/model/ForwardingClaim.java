package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;
import java.util.Objects;

/** One service mapping; TCP and UDP are created and deleted as a single operation. */
public record ForwardingClaim(String claimId, String floatingIpId, ForwardingSpec spec,
    Phase phase, ForwardingResources resources,
    long generation, String failure) implements Serializable {
  public enum Phase { CREATING, READY, FAILED, DELETING, DELETE_FAILED, DELETED }

  public ForwardingClaim {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(resources, "resources");
  }

  public static ForwardingClaim requested(String claimId, String floatingIpId, ForwardingSpec spec) {
    return new ForwardingClaim(claimId, floatingIpId, spec, Phase.CREATING,
        ForwardingResources.empty(), 0, null);
  }

  public boolean ownsPort() { return resources.externalPort() > 0; }

  public ForwardingClaim allocated(String address, int port) {
    // Reservation and rule creation belong to the same async operation and generation.
    return copy(Phase.CREATING, new ForwardingResources(address, port, null, null),
        generation, null);
  }

  public ForwardingClaim ready(ForwardingResources prepared) {
    return copy(Phase.READY, prepared, generation + 1, null);
  }

  public ForwardingClaim creationFailed(String detail) {
    return copy(Phase.FAILED, resources, generation + 1, detail);
  }

  public ForwardingClaim startDeletion() {
    return copy(Phase.DELETING, resources, generation + 1, failure);
  }

  public ForwardingClaim deleted() {
    return copy(Phase.DELETED, ForwardingResources.empty(), generation + 1, failure);
  }

  public ForwardingClaim deletionFailed(String detail) {
    return copy(Phase.DELETE_FAILED, resources, generation + 1, detail);
  }

  private ForwardingClaim copy(Phase nextPhase, ForwardingResources nextResources,
      long nextGeneration, String detail) {
    return new ForwardingClaim(claimId, floatingIpId, spec, nextPhase, nextResources,
        nextGeneration, detail);
  }
}
