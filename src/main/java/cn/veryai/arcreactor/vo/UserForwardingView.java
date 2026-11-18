package cn.veryai.arcreactor.vo;

import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingSpec;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import java.io.Serializable;

public record UserForwardingView(String forwardingId, String floatingIpId, String osFloatingIpId, int internalPort,
        InstanceForwardingSpec.Phase phase, PreparedForwarding resources, String failure, long generation)
        implements Serializable {
    public static UserForwardingView from(InstanceForwardingSpec spec, PreparedForwarding resources) {
        var operation = spec.operation();
        return new UserForwardingView(operation.claimId(), operation.floatingIpId(), operation.osFloatingIpId(),
                spec.internalPort(), operation.phase(), resources, operation.failure(), operation.generation());
    }
}
