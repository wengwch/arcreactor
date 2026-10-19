package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import java.io.Serializable;
import java.util.List;

/** Port IDs may be omitted when the instance workflow prepares its own network. */
public record ForwardingRequirement(List<String> floatingIpIds, String parentPortId, String portId,
    String fixedIp, int internalPort, String securityGroupId) implements Serializable {
  public ForwardingRequirement {
    floatingIpIds = List.copyOf(floatingIpIds);
    if (floatingIpIds.isEmpty() || floatingIpIds.stream().anyMatch(String::isBlank)
        || floatingIpIds.stream().distinct().count() != floatingIpIds.size()) {
      throw new IllegalArgumentException("ordered, distinct floating IP IDs are required");
    }
    boolean suppliedPorts = parentPortId != null || portId != null || fixedIp != null;
    if (suppliedPorts && (parentPortId == null || parentPortId.isBlank() || portId == null || portId.isBlank()
        || fixedIp == null || fixedIp.isBlank())) {
      throw new IllegalArgumentException("supplied network ports and fixed IP must be complete");
    }
    if (internalPort < 1 || internalPort > 65535) {
      throw new IllegalArgumentException("service port must be between 1 and 65535");
    }
    if (securityGroupId != null && securityGroupId.isBlank()) securityGroupId = null;
  }
  public ForwardingRequirement(List<String> floatingIpIds, int internalPort, String securityGroupId) {
    this(floatingIpIds, null, null, null, internalPort, securityGroupId);
  }

  public ForwardingSpec toSpec(String instanceId, String userId, String region, String projectId) {
    return new ForwardingSpec(instanceId, userId, region, projectId, portId, fixedIp, internalPort,
        securityGroupId, floatingIpIds);
  }
}
