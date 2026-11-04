package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;

public record ForwardingResources(String floatingIpAddress, int externalPort,
    String tcpForwardingId, String udpForwardingId) implements Serializable {
  public static ForwardingResources empty() {
    return new ForwardingResources(null, 0, null, null);
  }

  public ForwardingResources allocated(String address, int port) {
    return new ForwardingResources(address, port, null, null);
  }

  public ForwardingResources forwarding(boolean tcp, String id) {
    return new ForwardingResources(floatingIpAddress, externalPort,
        tcp ? id : tcpForwardingId, tcp ? udpForwardingId : id);
  }
}
