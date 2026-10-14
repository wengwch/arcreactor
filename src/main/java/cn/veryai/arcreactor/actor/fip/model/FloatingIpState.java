package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

public record FloatingIpState(Map<String, ForwardingClaim> claims, Set<String> cancelledClaims,
    Set<String> cancelledInstances) implements Serializable {
  public FloatingIpState {
    claims = Map.copyOf(claims);
    cancelledClaims = Set.copyOf(cancelledClaims);
    cancelledInstances = Set.copyOf(cancelledInstances);
  }
  public FloatingIpState updated(ForwardingClaim claim) {
    Map<String, ForwardingClaim> next = new HashMap<>(claims);
    next.put(claim.claimId(), claim);
    return new FloatingIpState(next, cancelledClaims, cancelledInstances);
  }
  public FloatingIpState cancelled(String claimId, String instanceId) {
    Set<String> nextClaims = new HashSet<>(cancelledClaims);
    Set<String> nextInstances = new HashSet<>(cancelledInstances);
    if (claimId != null) nextClaims.add(claimId);
    if (instanceId != null) nextInstances.add(instanceId);
    return new FloatingIpState(claims, nextClaims, nextInstances);
  }
}
