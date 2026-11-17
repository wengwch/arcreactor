package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;

public record ForwardingResources(String floatingIpAddress, String domain, int externalPort,
    String osTcpForwardingId, String osUdpForwardingId) implements Serializable {
  public static ForwardingResources empty() {
    return new ForwardingResources(null, null, 0, null, null);
  }
}
