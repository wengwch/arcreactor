package cn.veryai.arcreactor.vo;

import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import java.io.Serializable;
import java.util.List;

/** Public instance status; creation credentials remain available only in the creation response. */
public record InstanceView(
        String instanceId,
        String requestId,
        String phase,
        String hypervisorId,
        String reservationId,
        String novaOperationId,
        String novaServerId,
        String novaStatus,
        String failureCode,
        String failureMessage,
        long version,
        String userId,
        String shareId,
        String osShareId,
        List<PreparedForwarding> forwardings,
        PreparedNetwork network,
        String securityGroupId,
        String projectId) implements Serializable {

    public static InstanceView from(InstanceWorkflowState state) {
        return new InstanceView(state.instanceId(), state.requestId(), state.phase().name(), state.hypervisorId(),
                state.reservationId(), state.novaOperationId(), state.novaServerId(),
                state.novaStatus() == null ? null : state.novaStatus().name(), state.failureCode(),
                state.failureMessage(), state.version(),
                state.spec() == null || state.spec().share() == null ? null : state.spec().share().userId(),
                state.shareId(),
                state.preparedShare() == null ? null : state.preparedShare().osShareId(),
                state.preparedForwardings(),
                state.networkPreparation() == null ? null : state.networkPreparation().resources(),
                state.securityGroupId(), state.projectId());
    }
}
