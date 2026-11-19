package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/** Lifecycle state and confirmed resources of one claim; resources are absent until creation succeeds. */
public record InstanceForwardingState(int internalPort, ForwardingKind kind, String claimId,
    List<String> floatingIpIds, int candidateIndex, Phase phase, String failure,
    long generation, InstanceForwardingResources resources, String failureCode) implements Serializable {
  public enum Phase { CREATING, READY, FAILED, DELETING, DELETE_FAILED, DELETED }

  public InstanceForwardingState {
    if (internalPort < 1 || internalPort > 65535) throw new IllegalArgumentException("invalid internal port");
    Objects.requireNonNull(kind, "kind");
    if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
    floatingIpIds = List.copyOf(floatingIpIds);
    if (candidateIndex < 0 || candidateIndex >= floatingIpIds.size()) {
      throw new IllegalArgumentException("Invalid floating IP candidate index");
    }
    Objects.requireNonNull(phase, "phase");
    if (phase == Phase.READY) Objects.requireNonNull(resources, "resources");
  }

  public static InstanceForwardingState creating(int internalPort, ForwardingKind kind, String claimId,
      List<String> floatingIpIds) {
    return new InstanceForwardingState(internalPort, kind, claimId,
        floatingIpIds, 0, Phase.CREATING, null, 1, null, null);
  }

  public String floatingIpId() {
    return floatingIpIds.get(candidateIndex);
  }

  public boolean hasNextCandidate() {
    return candidateIndex + 1 < floatingIpIds.size();
  }

  public InstanceForwardingState nextCandidate() {
    return new InstanceForwardingState(internalPort, kind, claimId,
        floatingIpIds, candidateIndex + 1, Phase.CREATING, null, generation + 1, null, null);
  }

  public InstanceForwardingState ready(InstanceForwardingResources resources) {
    return new InstanceForwardingState(internalPort, kind, claimId,
        floatingIpIds, candidateIndex, Phase.READY, null, generation + 1, resources, null);
  }

  public InstanceForwardingState transition(Phase next, String failure) {
    return new InstanceForwardingState(internalPort, kind, claimId,
        floatingIpIds, candidateIndex, next, failure, generation + 1, resources, null);
  }

  public InstanceForwardingState failed(String code, String detail) {
    return new InstanceForwardingState(internalPort, kind, claimId,
        floatingIpIds, candidateIndex, Phase.FAILED, detail, generation + 1, resources, code);
  }

  public boolean inProgress() {
    return phase == Phase.CREATING || phase == Phase.DELETING;
  }
}
