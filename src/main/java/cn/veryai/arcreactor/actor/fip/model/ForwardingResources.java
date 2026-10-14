package cn.veryai.arcreactor.actor.fip.model;

import cn.veryai.arcreactor.openstack.OpenStackClient.ManagedNetworkResource;
import java.io.Serializable;

public record ForwardingResources(String floatingIpAddress, String securityGroupId, int externalPort,
    String tcpForwardingId, String udpForwardingId, String tcpRuleId, boolean tcpRuleOwned,
    String udpRuleId, boolean udpRuleOwned) implements Serializable {
  public static ForwardingResources empty() {
    return new ForwardingResources(null, null, 0, null, null, null, false, null, false);
  }

  public ForwardingResources allocated(String address, String groupId, int port) {
    return new ForwardingResources(address, groupId, port, null, null, null, false, null, false);
  }

  public ForwardingResources forwarding(boolean tcp, String id) {
    return new ForwardingResources(floatingIpAddress, securityGroupId, externalPort,
        tcp ? id : tcpForwardingId, tcp ? udpForwardingId : id,
        tcpRuleId, tcpRuleOwned, udpRuleId, udpRuleOwned);
  }

  public ForwardingResources rule(boolean tcp, ManagedNetworkResource rule) {
    return new ForwardingResources(floatingIpAddress, securityGroupId, externalPort,
        tcpForwardingId, udpForwardingId, tcp ? (rule == null ? null : rule.id()) : tcpRuleId,
        tcp ? rule != null && rule.owned() : tcpRuleOwned,
        tcp ? udpRuleId : (rule == null ? null : rule.id()), tcp ? udpRuleOwned : rule != null && rule.owned());
  }
}
