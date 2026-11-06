package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** One service mapping; TCP and UDP are created and deleted as a single operation. */
public record ForwardingClaim(String claimId, String floatingIpId, ForwardingSpec spec,
    Phase phase, ForwardingResources resources, boolean deleteRequested, boolean paused,
    long generation, Instant deletionDeadline, String failure) implements Serializable {
  public enum Phase { CREATING, READY, FAILED, DELETING, DELETED }

  public ForwardingClaim {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(resources, "resources");
    if (phase == Phase.DELETING) Objects.requireNonNull(deletionDeadline, "deletionDeadline");
  }

  public static ForwardingClaim requested(String claimId, String floatingIpId, ForwardingSpec spec) {
    return new ForwardingClaim(claimId, floatingIpId, spec, Phase.CREATING,
        ForwardingResources.empty(), false, false, 0, null, null);
  }

  public boolean ownsPort() { return resources.externalPort() > 0; }
  public boolean deleting() { return phase == Phase.DELETING; }

  public ForwardingClaim allocated(String address, int port) {
    // Reservation and rule creation belong to the same async operation and generation.
    return copy(Phase.CREATING, new ForwardingResources(address, port, null, null),
        deleteRequested, false, generation, null, null);
  }

  public ForwardingClaim ready(ForwardingResources prepared) {
    return copy(Phase.READY, prepared, deleteRequested, false, generation + 1, null, null);
  }

  public ForwardingClaim creationFailed(String detail, boolean uncertain) {
    return copy(Phase.FAILED, resources, deleteRequested, uncertain && ownsPort(),
        generation + 1, null, detail);
  }

  public ForwardingClaim requestDeletion() {
    // Keep the current operation generation so its completion can be recorded before cleanup.
    return copy(phase, resources, true, paused, generation, deletionDeadline, failure);
  }

  public ForwardingClaim startDeletion(Instant nextDeadline) {
    return copy(Phase.DELETING, resources, deleteRequested, false, generation + 1, nextDeadline, failure);
  }

  public ForwardingClaim deleted() {
    return copy(Phase.DELETED, ForwardingResources.empty(),
        deleteRequested, false, generation + 1, null, failure);
  }

  public ForwardingClaim deletionFailed(String detail) {
    return copy(Phase.DELETING, resources, deleteRequested, true, generation + 1, deletionDeadline, detail);
  }

  private ForwardingClaim copy(Phase nextPhase, ForwardingResources nextResources,
      boolean nextDeleteRequested, boolean nextPaused, long nextGeneration, Instant nextDeadline, String detail) {
    return new ForwardingClaim(claimId, floatingIpId, spec, nextPhase, nextResources,
        nextDeleteRequested, nextPaused, nextGeneration, nextDeadline, detail);
  }
}
