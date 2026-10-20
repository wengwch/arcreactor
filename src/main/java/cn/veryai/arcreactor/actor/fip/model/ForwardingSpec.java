package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;
import java.util.List;

/** The internal service endpoint; the destination actor identifies the floating IP. */
public record ForwardingSpec(String instanceId, String userId, String regionId, String projectId,
    String internalPortId, String fixedIp, int internalPort, String securityGroupId) implements Serializable {
  public ForwardingSpec {
    for (String value : List.of(instanceId, userId, regionId, projectId, internalPortId, fixedIp)) {
      if (value.isBlank()) throw new IllegalArgumentException("forwarding identifiers must not be blank");
    }
    if (internalPort < 1 || internalPort > 65535) throw new IllegalArgumentException("invalid internal port");
    if (securityGroupId != null && securityGroupId.isBlank()) securityGroupId = null;
  }
}
