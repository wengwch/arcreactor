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
import java.util.Objects;
import java.util.Set;

public record FloatingIpState(boolean enabled, FloatingIpIdentity identity,
    Map<String, ForwardingClaim> claims, Map<String, ForwardingOutcome> outcomes,
    @JsonSerialize(using = PortUsageJson.Serializer.class)
    @JsonDeserialize(using = PortUsageJson.Deserializer.class) BitSet usedPorts,
    byte[] importedPortMask) implements Serializable {
  public FloatingIpState {
    if (enabled) Objects.requireNonNull(identity, "identity");
    claims = Map.copyOf(claims);
    outcomes = Map.copyOf(outcomes);
    importedPortMask = Objects.requireNonNull(importedPortMask, "importedPortMask").clone();
    usedPorts = (BitSet) Objects.requireNonNull(usedPorts, "usedPorts").clone();
    usedPorts.or(BitSet.valueOf(importedPortMask));
  }

  public static FloatingIpState empty() {
    return new FloatingIpState(false, null, Map.of(), Map.of(), new BitSet(), new byte[0]);
  }

  @Override public BitSet usedPorts() { return (BitSet) usedPorts.clone(); }
  @Override public byte[] importedPortMask() { return importedPortMask.clone(); }

  public FloatingIpState enabled(FloatingIpIdentity identity, byte[] portMask) {
    BitSet ports = BitSet.valueOf(portMask);
    for (ForwardingClaim claim : claims.values()) {
      if (claim.ownsPort()) ports.set(claim.resources().externalPort() - FloatingIpSettings.FIRST_PORT);
    }
    return new FloatingIpState(true, identity, claims, outcomes, ports, portMask);
  }

  public FloatingIpState disabled() {
    return new FloatingIpState(false, identity, claims, outcomes, usedPorts, importedPortMask);
  }

  /** Known managed rules stay owned by their claim, so deleting that claim can release its port. */
  public byte[] importedMask(List<PortForwardingSnapshot> forwardings) {
    Map<String, Integer> managedRules = new HashMap<>();
    for (ForwardingClaim claim : claims.values()) {
      if (!claim.ownsPort()) continue;
      ForwardingResources resources = claim.resources();
      if (resources.tcpForwardingId() != null) managedRules.put(resources.tcpForwardingId(), resources.externalPort());
      if (resources.udpForwardingId() != null) managedRules.put(resources.udpForwardingId(), resources.externalPort());
    }
    BitSet ports = new BitSet();
    for (PortForwardingSnapshot forwarding : forwardings) {
      int port = forwarding.externalPort();
      if (port >= FloatingIpSettings.FIRST_PORT && port <= FloatingIpSettings.LAST_PORT
          && !Objects.equals(managedRules.get(forwarding.id()), port)) {
        ports.set(port - FloatingIpSettings.FIRST_PORT);
      }
    }
    return ports.toByteArray();
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
    return new FloatingIpState(enabled, identity, next, outcomes, ports, importedPortMask);
  }

  public FloatingIpState creationFailed(String claimId, String failure, boolean uncertain, Instant failedAt) {
    return completed(claims.get(claimId).creationFailed(failure, uncertain), failedAt);
  }

  public FloatingIpState deleted(String claimId, Instant deletedAt) {
    return completed(claims.get(claimId).deleted(), deletedAt);
  }

  private FloatingIpState completed(ForwardingClaim claim, Instant completedAt) {
    FloatingIpState next = updated(claim);
    if (claim.ownsPort() || claim.paused()) return next;
    Map<String, ForwardingClaim> remaining = new HashMap<>(next.claims);
    remaining.remove(claim.claimId());
    Map<String, ForwardingOutcome> nextOutcomes = new HashMap<>(outcomes);
    nextOutcomes.put(claim.claimId(), ForwardingOutcome.from(claim, completedAt));
    return new FloatingIpState(enabled, identity, remaining, nextOutcomes, next.usedPorts, importedPortMask);
  }

  public FloatingIpState cancelled(String claimId, Instant cancelledAt) {
    Map<String, ForwardingOutcome> nextOutcomes = new HashMap<>(outcomes);
    nextOutcomes.compute(claimId, (id, previous) -> previous == null
        ? ForwardingOutcome.cancelled(cancelledAt) : previous.deleted(cancelledAt));
    return new FloatingIpState(enabled, identity, claims, nextOutcomes, usedPorts, importedPortMask);
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
    return new FloatingIpState(enabled, identity, claims, nextOutcomes, usedPorts, importedPortMask);
  }
}
