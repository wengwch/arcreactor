package cn.veryai.arcreactor.actor.fip.model;

import cn.veryai.arcreactor.actor.fip.FloatingIpSettings;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.Serializable;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public record FloatingIpState(Map<String, ForwardingClaim> claims, Set<String> cancelledClaims,
    Set<String> cancelledInstances,
    @JsonSerialize(using = PortUsageJson.Serializer.class)
    @JsonDeserialize(using = PortUsageJson.Deserializer.class) BitSet usedPorts) implements Serializable {
  public FloatingIpState {
    claims = Map.copyOf(claims);
    cancelledClaims = Set.copyOf(cancelledClaims);
    cancelledInstances = Set.copyOf(cancelledInstances);
    if (usedPorts == null) {
      usedPorts = new BitSet();
      for (ForwardingClaim claim : claims.values()) {
        int port = claim.resources().externalPort();
        if (port >= FloatingIpSettings.FIRST_PORT && port <= FloatingIpSettings.LAST_PORT) {
          usedPorts.set(port - FloatingIpSettings.FIRST_PORT);
        }
      }
    } else {
      usedPorts = (BitSet) usedPorts.clone();
    }
  }
  @Override public BitSet usedPorts() { return (BitSet) usedPorts.clone(); }

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
    return new FloatingIpState(next, cancelledClaims, cancelledInstances, ports);
  }
  public FloatingIpState cancelled(String claimId, String instanceId) {
    Set<String> nextClaims = new HashSet<>(cancelledClaims);
    Set<String> nextInstances = new HashSet<>(cancelledInstances);
    if (claimId != null) nextClaims.add(claimId);
    if (instanceId != null) nextInstances.add(instanceId);
    return new FloatingIpState(claims, nextClaims, nextInstances, usedPorts);
  }
}
