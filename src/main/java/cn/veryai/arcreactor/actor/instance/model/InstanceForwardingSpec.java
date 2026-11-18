package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public record InstanceForwardingSpec(int internalPort, ForwardingKind kind, Operation operation)
    implements Serializable {
  public enum Phase { CREATING, READY, FAILED, DELETING, DELETE_FAILED, DELETED }

  public record Operation(String claimId, boolean instanceInitialization, List<String> floatingIpIds, int candidateIndex,
      String osFloatingIpId, Phase phase, String failure, long generation) implements Serializable {
    public Operation {
      if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
      floatingIpIds = List.copyOf(floatingIpIds);
      if (candidateIndex < 0 || candidateIndex >= floatingIpIds.size()) {
        throw new IllegalArgumentException("Invalid floating IP candidate index");
      }
      Objects.requireNonNull(phase, "phase");
    }

    public String floatingIpId() {
      return floatingIpIds.get(candidateIndex);
    }
  }

  public InstanceForwardingSpec {
    if (internalPort < 1 || internalPort > 65535) throw new IllegalArgumentException("invalid internal port");
    Objects.requireNonNull(kind, "kind");
  }

  static InstanceForwardingSpec system(int internalPort) {
    return new InstanceForwardingSpec(internalPort, ForwardingKind.SYSTEM, null);
  }

  public static InstanceForwardingSpec creating(int internalPort, ForwardingKind kind, String claimId,
      boolean instanceInitialization, List<String> floatingIpIds, String osFloatingIpId) {
    return new InstanceForwardingSpec(internalPort, kind,
        new Operation(claimId, instanceInitialization, floatingIpIds, 0, osFloatingIpId, Phase.CREATING, null, 1));
  }

  public boolean hasNextCandidate() {
    return operation.candidateIndex() + 1 < operation.floatingIpIds().size();
  }

  public InstanceForwardingSpec nextCandidate() {
    return new InstanceForwardingSpec(internalPort, kind, new Operation(operation.claimId(), operation.instanceInitialization(),
        operation.floatingIpIds(), operation.candidateIndex() + 1, null, Phase.CREATING, null, operation.generation() + 1));
  }

  public InstanceForwardingSpec transition(Phase next, String failure) {
    return new InstanceForwardingSpec(internalPort, kind, new Operation(operation.claimId(), operation.instanceInitialization(),
        operation.floatingIpIds(), operation.candidateIndex(), operation.osFloatingIpId(), next, failure, operation.generation() + 1));
  }

  public boolean inProgress() {
    return operation != null && (operation.phase() == Phase.CREATING || operation.phase() == Phase.DELETING);
  }

  public ForwardingDeletionTarget deletionTarget() {
    return new ForwardingDeletionTarget(operation.claimId(), operation.floatingIpId(), internalPort);
  }
}
