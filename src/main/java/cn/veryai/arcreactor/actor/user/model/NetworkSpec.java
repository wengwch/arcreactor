package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;

/** A private IPv4 network; null gateway lets Neutron choose the subnet gateway. */
public record NetworkSpec(String regionId, String name, String ipv4CIDR, String dns,
    String gateway) implements Serializable {
  public NetworkSpec {
    requireText(regionId, "regionId");
    requireText(name, "name");
    requireText(ipv4CIDR, "ipv4CIDR");
    // Neutron validates the CIDR, gateway and DNS addresses before creating the subnet.
    dns = dns == null ? "" : dns;
    gateway = gateway == null || gateway.isBlank() ? null : gateway;
  }

  public NetworkSpec(String regionId, String name, String ipv4CIDR) {
    this(regionId, name, ipv4CIDR, null, null);
  }

  private static void requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
  }
}
