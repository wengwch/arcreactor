package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import java.io.Serializable;

/** Confirmed OpenStack resources; claim identity and forwarding requirements live in InstanceForwardingState. */
public record InstanceForwardingResources(String osFloatingIpId, String floatingIp, String domain,
    int externalPort, String osTcpForwardingId, String osUdpForwardingId) implements Serializable {
  public static InstanceForwardingResources from(ForwardingClaim claim) {
    return new InstanceForwardingResources(claim.osFloatingIpId(), claim.resources().floatingIpAddress(),
        claim.resources().domain(), claim.resources().externalPort(),
        claim.resources().osTcpForwardingId(), claim.resources().osUdpForwardingId());
  }
}
