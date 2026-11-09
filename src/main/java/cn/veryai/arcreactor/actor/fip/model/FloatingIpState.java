package cn.veryai.arcreactor.actor.fip.model;

import cn.veryai.arcreactor.actor.fip.FloatingIpSettings;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serializable;
import java.time.Instant;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record FloatingIpState(boolean enabled, FloatingIpIdentity identity,
    Map<String, ForwardingClaim> claims, Map<String, ForwardingOutcome> outcomes,
    @JsonSerialize(using = PortUsageJson.Serializer.class)
    @JsonDeserialize(using = PortUsageJson.Deserializer.class) BitSet usedPorts) implements Serializable {
  public FloatingIpState {
    if (enabled) Objects.requireNonNull(identity, "identity");
    claims = Map.copyOf(claims);
    outcomes = Map.copyOf(outcomes);
    usedPorts = (BitSet) Objects.requireNonNull(usedPorts, "usedPorts").clone();
  }

  public static FloatingIpState empty() {
    return new FloatingIpState(false, null, Map.of(), Map.of(), new BitSet());
  }

  @Override public BitSet usedPorts() { return (BitSet) usedPorts.clone(); }

  /** Applies the complete port usage persisted by the enable event. */
  public FloatingIpState enabled(FloatingIpIdentity identity, byte[] portMask) {
    return new FloatingIpState(true, identity, claims, outcomes, BitSet.valueOf(portMask));
  }

  public FloatingIpState disabled() {
    return new FloatingIpState(false, identity, claims, outcomes, usedPorts);
  }

  public FloatingIpState updated(ForwardingClaim claim) {
    Map<String, ForwardingClaim> next = new HashMap<>(claims);
    ForwardingClaim previous = next.put(claim.claimId(), claim);
    BitSet ports = usedPorts();
    int oldPort = previous == null ? 0 : previous.resources().externalPort();
    int newPort = claim.resources().externalPort();
    if (oldPort > 0 && oldPort != newPort
        && next.values().stream().noneMatch(other -> other.resources().externalPort() == oldPort)) {
      ports.clear(oldPort - FloatingIpSettings.FIRST_PORT);
    }
    if (newPort > 0) {
      if (newPort < FloatingIpSettings.FIRST_PORT || newPort > FloatingIpSettings.LAST_PORT) {
        throw new IllegalStateException("Forwarding external port is outside 10000-40000");
      }
      ports.set(newPort - FloatingIpSettings.FIRST_PORT);
    }
    return new FloatingIpState(enabled, identity, next, outcomes, ports);
  }

  public FloatingIpState creationFailed(String claimId, String failure, Instant failedAt) {
    return completed(claims.get(claimId).creationFailed(failure), failedAt);
  }

  public FloatingIpState deleted(String claimId, Instant deletedAt) {
    ForwardingClaim claim = claims.get(claimId);
    if (claim != null) return completed(claim.deleted(), deletedAt);
    Map<String, ForwardingOutcome> nextOutcomes = new HashMap<>(outcomes);
    nextOutcomes.compute(claimId, (id, previous) -> previous == null
        ? ForwardingOutcome.tombstone(deletedAt) : previous.deleted(deletedAt));
    return new FloatingIpState(enabled, identity, claims, nextOutcomes, usedPorts);
  }

  private FloatingIpState completed(ForwardingClaim claim, Instant completedAt) {
    FloatingIpState next = updated(claim);
    if (claim.ownsPort()) return next;
    Map<String, ForwardingClaim> remaining = new HashMap<>(next.claims);
    remaining.remove(claim.claimId());
    Map<String, ForwardingOutcome> nextOutcomes = new HashMap<>(outcomes);
    nextOutcomes.put(claim.claimId(), ForwardingOutcome.from(claim, completedAt));
    return new FloatingIpState(enabled, identity, remaining, nextOutcomes, next.usedPorts);
  }

  public Set<String> expiredOutcomes(Instant cutoff) {
    Set<String> expired = new HashSet<>();
    outcomes.forEach((id, outcome) -> {
      if (!claims.containsKey(id) && !outcome.completedAt().isAfter(cutoff)) expired.add(id);
    });
    return Set.copyOf(expired);
  }

  public FloatingIpState cleanOutcomes(Set<String> claimIds) {
    Map<String, ForwardingOutcome> nextOutcomes = new HashMap<>(outcomes);
    claimIds.forEach(nextOutcomes::remove);
    return new FloatingIpState(enabled, identity, claims, nextOutcomes, usedPorts);
  }
}
