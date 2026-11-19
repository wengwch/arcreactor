package cn.veryai.arcreactor.actor.fip.model;

import cn.veryai.arcreactor.actor.fip.FloatingIpSettings;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serializable;
import java.time.Instant;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record FloatingIpState(Status status, FloatingIpIdentity identity,
    Map<String, ForwardingClaim> claims, Map<String, ForwardingOutcome> outcomes,
    @JsonSerialize(using = PortUsageJson.Serializer.class)
    @JsonDeserialize(using = PortUsageJson.Deserializer.class) BitSet usedPorts) implements Serializable {
  public enum Status { DISABLED, ENABLED, DESTROYING, DESTROYED }

  public FloatingIpState {
    claims = Map.copyOf(claims);
    outcomes = Map.copyOf(outcomes);
    usedPorts = (BitSet) usedPorts.clone();
  }

  public static FloatingIpState empty() {
    return new FloatingIpState(Status.DISABLED, null, Map.of(), Map.of(), new BitSet());
  }

  /** Builds port usage within the actor's allocatable range. */
  public static BitSet portMask(List<Integer> externalPorts) {
    BitSet ports = new BitSet();
    for (int port : externalPorts) {
      if (port >= FloatingIpSettings.FIRST_PORT && port <= FloatingIpSettings.LAST_PORT) {
        ports.set(port - FloatingIpSettings.FIRST_PORT);
      }
    }
    return ports;
  }

  @Override public BitSet usedPorts() { return (BitSet) usedPorts.clone(); }

  /** Applies the complete port usage persisted by the enable event. */
  public FloatingIpState enabled(FloatingIpIdentity identity, byte[] portMask) {
    return new FloatingIpState(Status.ENABLED, identity, claims, outcomes, BitSet.valueOf(portMask));
  }

  public FloatingIpState disabled() {
    return new FloatingIpState(Status.DISABLED, identity, claims, outcomes, usedPorts);
  }

  public FloatingIpState destructionStarted() {
    return new FloatingIpState(Status.DESTROYING, identity, claims, outcomes, usedPorts);
  }

  public FloatingIpState destructionCompleted() {
    return new FloatingIpState(Status.DESTROYED, identity, claims, outcomes, usedPorts);
  }

  /** Updates lifecycle data without changing port reservations. */
  public FloatingIpState updated(ForwardingClaim claim) {
    Map<String, ForwardingClaim> next = new HashMap<>(claims);
    next.put(claim.claimId(), claim);
    return new FloatingIpState(status, identity, next, outcomes, usedPorts);
  }

  /** Records an allocated claim and reserves its external port. */
  public FloatingIpState allocated(ForwardingClaim claim) {
    Map<String, ForwardingClaim> next = new HashMap<>(claims);
    next.put(claim.claimId(), claim);
    BitSet ports = usedPorts();
    ports.set(claim.resources().externalPort() - FloatingIpSettings.FIRST_PORT);
    return new FloatingIpState(status, identity, next, outcomes, ports);
  }

  public FloatingIpState creationFailed(String claimId, String failure, Instant failedAt) {
    ForwardingClaim claim = claims.get(claimId).creationFailed(failure);
    if (claim.ownsPort()) return updated(claim);
    Map<String, ForwardingClaim> nextClaims = new HashMap<>(claims);
    nextClaims.remove(claimId);
    Map<String, ForwardingOutcome> nextOutcomes = new HashMap<>(outcomes);
    nextOutcomes.put(claimId, ForwardingOutcome.from(claim, failedAt));
    return new FloatingIpState(status, identity, nextClaims, nextOutcomes, usedPorts);
  }

  public FloatingIpState deleted(String claimId, Instant deletedAt) {
    Map<String, ForwardingClaim> nextClaims = new HashMap<>(claims);
    ForwardingClaim claim = nextClaims.remove(claimId);
    Map<String, ForwardingOutcome> nextOutcomes = new HashMap<>(outcomes);
    BitSet ports = usedPorts();
    if (claim != null) {
      ports.clear(claim.resources().externalPort() - FloatingIpSettings.FIRST_PORT);
      nextOutcomes.put(claimId, ForwardingOutcome.from(claim.deleted(), deletedAt));
    } else {
      nextOutcomes.compute(claimId, (id, previous) -> previous == null
          ? ForwardingOutcome.tombstone(deletedAt) : previous.deleted(deletedAt));
    }
    return new FloatingIpState(status, identity, nextClaims, nextOutcomes, ports);
  }

  public Set<String> expiredOutcomes(Instant cutoff) {
    Set<String> expired = new HashSet<>();
    outcomes.forEach((id, outcome) -> {
      if (!outcome.completedAt().isAfter(cutoff)) expired.add(id);
    });
    return Set.copyOf(expired);
  }

  public FloatingIpState cleanOutcomes(Set<String> claimIds) {
    Map<String, ForwardingOutcome> nextOutcomes = new HashMap<>(outcomes);
    claimIds.forEach(nextOutcomes::remove);
    return new FloatingIpState(status, identity, claims, nextOutcomes, usedPorts);
  }
}
