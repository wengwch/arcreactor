package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.io.Serializable;
import java.time.Instant;

public record InstanceWorkflowState(
        String instanceId,
        String requestId,
        InstancePhase phase,
        InstanceSpec spec,
        String hypervisorId,
        String reservationId,
        String novaOperationId,
        String novaServerId,
        OpenStackClient.NovaServer.Status novaStatus,
        Instant provisioningStartedAt,
        Instant provisioningDeadline,
        long pollingGeneration,
        int schedulingAttempts,
        String failureCode,
        String failureMessage,
        long version,
        Instant forwardingPreparationDeadline,
        PreparedForwarding preparedForwarding,
        NetworkPreparation networkPreparation,
        int forwardingCandidateIndex) implements Serializable {

    public static InstanceWorkflowState empty(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        return new InstanceWorkflowState(instanceId, null, InstancePhase.NEW, null, null, null,
                null, null, null, null, null, 0, 0, null, null, 0, null, null, null, 0);
    }

    public String projectId() { return spec == null ? null : spec.projectId(); }

    public PreparedShare preparedShare() {
        return spec == null || spec.share() == null ? null : spec.share().preparedShare();
    }

    public ShareSpec shareSpec() {
        return spec.share().spec();
    }

    public InstanceWorkflowState preparingForwarding(Instant deadline) {
        return new InstanceWorkflowState(instanceId, requestId, InstancePhase.PREPARING_FORWARDING, spec,
                hypervisorId, reservationId, novaOperationId, novaServerId, novaStatus,
                provisioningStartedAt, provisioningDeadline, pollingGeneration + 1, schedulingAttempts,
                failureCode, failureMessage, version + 1, deadline, null, networkPreparation, 0);
    }

    public InstanceWorkflowState forwardingPrepared(PreparedForwarding forwarding) {
        return new InstanceWorkflowState(instanceId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage, version + 1,
                forwardingPreparationDeadline, forwarding, networkPreparation, forwardingCandidateIndex);
    }

    public InstanceWorkflowState forwardingCandidateSelected(int candidateIndex) {
        return new InstanceWorkflowState(instanceId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage, version + 1,
                forwardingPreparationDeadline, preparedForwarding,
                networkPreparation, candidateIndex);
    }

    public InstanceWorkflowState preparingNetwork(Instant deadline) {
        return withNetwork(new NetworkPreparation(deadline, PreparedNetwork.empty()));
    }

    public InstanceWorkflowState networkPrepared(PreparedNetwork resources) {
        return withNetwork(new NetworkPreparation(networkPreparation.deadline(), resources));
    }

    private InstanceWorkflowState withNetwork(NetworkPreparation preparation) {
        return new InstanceWorkflowState(instanceId, requestId, InstancePhase.PREPARING_NETWORK, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage, version + 1,
                forwardingPreparationDeadline, preparedForwarding, preparation, forwardingCandidateIndex);
    }

    public InstanceWorkflowState created(String nextRequestId, InstanceSpec nextSpec) {
        return copy(nextRequestId, InstancePhase.NEW, nextSpec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, schedulingAttempts, null, null);
    }

    public InstanceWorkflowState scheduling(int attempts) {
        return copy(requestId, InstancePhase.SCHEDULING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, attempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState hypervisorSelected(String nextHypervisorId, String nextReservationId,
            int attempts) {
        return copy(requestId, InstancePhase.SCHEDULING, spec, nextHypervisorId, nextReservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, attempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState provisioning(String operationId, Instant startedAt, Instant deadline) {
        return copy(requestId, InstancePhase.PROVISIONING, spec, hypervisorId, reservationId,
                operationId, novaServerId, novaStatus, startedAt, deadline,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState serverObserved(String serverId, OpenStackClient.NovaServer.Status status) {
        return copy(requestId, InstancePhase.PROVISIONING, spec, hypervisorId, reservationId,
                novaOperationId, serverId, status, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState confirming() {
        return copy(requestId, InstancePhase.CONFIRMING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, OpenStackClient.NovaServer.Status.ACTIVE,
                provisioningStartedAt, provisioningDeadline, pollingGeneration + 1,
                schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState compensating(String code, String message) {
        return copy(requestId, InstancePhase.COMPENSATING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, code, message);
    }

    public InstanceWorkflowState active() {
        return copy(requestId, InstancePhase.ACTIVE, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, OpenStackClient.NovaServer.Status.ACTIVE,
                provisioningStartedAt, provisioningDeadline, pollingGeneration,
                schedulingAttempts, null, null);
    }

    public InstanceWorkflowState failed(String code, String message) {
        return copy(requestId, InstancePhase.FAILED, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, schedulingAttempts, code, message);
    }

    public InstanceWorkflowState touch() {
        return copy(requestId, phase, spec, hypervisorId, reservationId, novaOperationId,
                novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage);
    }

    private InstanceWorkflowState copy(String nextRequestId, InstancePhase nextPhase, InstanceSpec nextSpec,
            String nextHypervisorId, String nextReservationId, String nextOperationId,
            String nextServerId, OpenStackClient.NovaServer.Status nextNovaStatus, Instant nextStartedAt,
            Instant nextDeadline, long nextGeneration, int nextAttempts, String nextFailureCode,
            String nextFailureMessage) {
        return new InstanceWorkflowState(instanceId, nextRequestId, nextPhase, nextSpec,
                nextHypervisorId, nextReservationId, nextOperationId, nextServerId, nextNovaStatus,
                nextStartedAt, nextDeadline, nextGeneration, nextAttempts,
                nextFailureCode, nextFailureMessage, version + 1, forwardingPreparationDeadline, preparedForwarding, networkPreparation, forwardingCandidateIndex);
    }
}
