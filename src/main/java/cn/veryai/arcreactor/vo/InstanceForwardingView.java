package cn.veryai.arcreactor.vo;

import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingResources;
import java.io.Serializable;
import java.util.Objects;

/** Public forwarding details assembled from the claim state and its confirmed resources. */
public record InstanceForwardingView(
        String claimId,
        String floatingIpId,
        String osFloatingIpId,
        String floatingIp,
        String domain,
        int internalPort,
        int externalPort,
        String osTcpForwardingId,
        String osUdpForwardingId,
        ForwardingKind kind) implements Serializable {

    public static InstanceForwardingView from(InstanceForwardingState forwarding) {
        InstanceForwardingResources resources = Objects.requireNonNull(forwarding.resources(), "resources");
        return new InstanceForwardingView(forwarding.claimId(), forwarding.floatingIpId(), resources.osFloatingIpId(),
                resources.floatingIp(), resources.domain(), forwarding.internalPort(), resources.externalPort(),
                resources.osTcpForwardingId(), resources.osUdpForwardingId(), forwarding.kind());
    }
}
