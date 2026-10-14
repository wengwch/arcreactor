package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import java.io.Serializable;
import java.util.List;

/** Ports are supplied by network preparation; parentPortId is attached to Nova (including trunks). */
public record ForwardingRequirement(List<String> floatingIpIds, String parentPortId, String portId,
    String fixedIp, int internalPort, String securityGroupId) implements Serializable {
  public ForwardingRequirement {
    floatingIpIds = List.copyOf(floatingIpIds);
    if (floatingIpIds.isEmpty() || floatingIpIds.stream().anyMatch(String::isBlank)
        || floatingIpIds.stream().distinct().count() != floatingIpIds.size()) {
      throw new IllegalArgumentException("ordered, distinct floating IP IDs are required");
    }
    if (parentPortId == null || parentPortId.isBlank() || portId == null || portId.isBlank()
        || fixedIp == null || fixedIp.isBlank() || internalPort < 1 || internalPort > 65535) {
      throw new IllegalArgumentException("prepared network ports, fixed IP and service port are required");
    }
    if (securityGroupId != null && securityGroupId.isBlank()) securityGroupId = null;
  }
  public ForwardingSpec toSpec(String instanceId, String userId, String region, String projectId) {
    return new ForwardingSpec(instanceId, userId, region, projectId, portId, fixedIp, internalPort,
        securityGroupId, floatingIpIds);
  }
}
