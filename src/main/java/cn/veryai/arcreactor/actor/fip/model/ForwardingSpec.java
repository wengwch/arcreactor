package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;
import java.util.List;

/** Candidate IDs are tried in list order; each mapping reserves both TCP and UDP. */
public record ForwardingSpec(String instanceId, String userId, String regionId, String projectId,
    String portId, String fixedIp, int internalPort, String securityGroupId,
    List<String> floatingIpIds) implements Serializable {
  public ForwardingSpec {
    for (String value : List.of(instanceId, userId, regionId, projectId, portId, fixedIp)) {
      if (value.isBlank()) throw new IllegalArgumentException("forwarding identifiers must not be blank");
    }
    if (internalPort < 1 || internalPort > 65535) throw new IllegalArgumentException("invalid internal port");
    floatingIpIds = List.copyOf(floatingIpIds);
    if (floatingIpIds.isEmpty() || floatingIpIds.stream().anyMatch(String::isBlank)
        || floatingIpIds.stream().distinct().count() != floatingIpIds.size()) {
      throw new IllegalArgumentException("ordered, distinct floating IP IDs are required");
    }
    if (securityGroupId != null && securityGroupId.isBlank()) securityGroupId = null;
  }
}
