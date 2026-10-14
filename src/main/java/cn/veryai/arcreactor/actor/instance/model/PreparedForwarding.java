package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import java.io.Serializable;

public record PreparedForwarding(String claimId, String floatingIpId, String floatingIp,
    int externalPort, String tcpForwardingId, String udpForwardingId) implements Serializable {
  public static PreparedForwarding from(ForwardingClaim claim) {
    return new PreparedForwarding(claim.claimId(), claim.floatingIpId(), claim.resources().floatingIpAddress(),
        claim.resources().externalPort(), claim.resources().tcpForwardingId(), claim.resources().udpForwardingId());
  }
}
