package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import java.io.Serializable;

public record PreparedForwarding(String claimId, String osFloatingIpId, String floatingIp,
    int internalPort, int externalPort, String osTcpForwardingId, String osUdpForwardingId) implements Serializable {
  public static PreparedForwarding from(ForwardingClaim claim) {
    return new PreparedForwarding(claim.claimId(), claim.osFloatingIpId(), claim.resources().floatingIpAddress(),
        claim.spec().internalPort(), claim.resources().externalPort(),
        claim.resources().osTcpForwardingId(), claim.resources().osUdpForwardingId());
  }
}
