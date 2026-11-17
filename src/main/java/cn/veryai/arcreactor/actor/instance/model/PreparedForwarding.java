package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import java.io.Serializable;

public record PreparedForwarding(String claimId, String floatingIpId, String osFloatingIpId, String floatingIp, String domain,
    int internalPort, int externalPort, String osTcpForwardingId, String osUdpForwardingId,
    ForwardingKind kind) implements Serializable {
  public static PreparedForwarding from(ForwardingClaim claim) {
    return new PreparedForwarding(claim.claimId(), claim.floatingIpId(), claim.osFloatingIpId(), claim.resources().floatingIpAddress(),
        claim.resources().domain(), claim.spec().internalPort(), claim.resources().externalPort(),
        claim.resources().osTcpForwardingId(), claim.resources().osUdpForwardingId(), claim.spec().kind());
  }
}
