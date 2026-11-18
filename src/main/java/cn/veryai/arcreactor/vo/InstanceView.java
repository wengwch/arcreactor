package cn.veryai.arcreactor.vo;

import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Public instance status; creation credentials remain available only in the creation response. */
public record InstanceView(
        String instanceId,
        String requestId,
        String phase,
        String hypervisorId,
        String reservationId,
        String novaOperationId,
        String osNovaServerId,
        String novaStatus,
        String failureCode,
        String failureMessage,
        long version,
        String userId,
        String shareId,
        String osShareId,
        List<PreparedForwarding> forwardings,
        PreparedNetwork network,
        String osSecurityGroupId,
        String osProjectId,
        Management management) implements Serializable {

    public record Management(Map<String, UserForwardingView> userForwardings, InstanceQosUpdate qosUpdate)
            implements Serializable {
        static Management from(InstanceWorkflowState state) {
            Map<String, UserForwardingView> forwardings = new HashMap<>();
            state.deletedUserForwardings().forEach((id, deleted) ->
                    forwardings.put(id, UserForwardingView.from(deleted.spec(), deleted.resources())));
            for (var spec : state.currentUserForwardings()) {
                var forwarding = UserForwardingView.from(spec, state.preparedForwardings().get(spec.internalPort()));
                forwardings.put(forwarding.forwardingId(), forwarding);
            }
            return new Management(Map.copyOf(forwardings), state.qosUpdate());
        }
    }

    public static InstanceView from(InstanceWorkflowState state) {
        return new InstanceView(state.instanceId(), state.requestId(), state.phase().name(), state.hypervisorId(),
                state.reservationId(), state.novaOperationId(), state.osNovaServerId(),
                state.novaStatus() == null ? null : state.novaStatus().name(), state.failureCode(),
                state.failureMessage(), state.version(),
                state.userId(),
                state.shareId(),
                state.preparedShare() == null ? null : state.preparedShare().osShareId(),
                state.visibleForwardings(),
                state.networkPreparation() == null ? null : state.networkPreparation().resources(),
                state.osSecurityGroupId(), state.osProjectId(),
                Management.from(state));
    }
}
