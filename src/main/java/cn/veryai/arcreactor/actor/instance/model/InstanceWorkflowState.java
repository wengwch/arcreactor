package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.openstack.NovaClient;
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
        NovaClient.NovaServer.Status novaStatus,
        Instant provisioningStartedAt,
        Instant provisioningDeadline,
        long pollingGeneration,
        int schedulingAttempts,
        String failureCode,
        String failureMessage,
        long version) implements Serializable {

    public static InstanceWorkflowState empty(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        return new InstanceWorkflowState(instanceId, null, InstancePhase.NEW, null, null, null,
                null, null, null, null, null, 0, 0, null, null, 0);
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
        return copy(requestId, InstancePhase.RESERVING, spec, nextHypervisorId, nextReservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, attempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState reserved() {
        return copy(requestId, InstancePhase.RESERVED, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState novaCreating(String operationId, Instant startedAt, Instant deadline) {
        return copy(requestId, InstancePhase.NOVA_CREATING, spec, hypervisorId, reservationId,
                operationId, novaServerId, novaStatus, startedAt, deadline,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState retryNovaCreate() {
        return copy(requestId, InstancePhase.NOVA_CREATING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState building(String serverId, NovaClient.NovaServer.Status status) {
        return copy(requestId, InstancePhase.BUILDING, spec, hypervisorId, reservationId,
                novaOperationId, serverId, status, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState reconciling() {
        return copy(requestId, InstancePhase.RECONCILING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState novaStatus(NovaClient.NovaServer.Status status) {
        return copy(requestId, phase, spec, hypervisorId, reservationId, novaOperationId,
                novaServerId, status, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState confirming() {
        return copy(requestId, InstancePhase.CONFIRMING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, NovaClient.NovaServer.Status.ACTIVE,
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
                novaOperationId, novaServerId, NovaClient.NovaServer.Status.ACTIVE,
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
            String nextServerId, NovaClient.NovaServer.Status nextNovaStatus, Instant nextStartedAt,
            Instant nextDeadline, long nextGeneration, int nextAttempts, String nextFailureCode,
            String nextFailureMessage) {
        return new InstanceWorkflowState(instanceId, nextRequestId, nextPhase, nextSpec,
                nextHypervisorId, nextReservationId, nextOperationId, nextServerId, nextNovaStatus,
                nextStartedAt, nextDeadline, nextGeneration, nextAttempts,
                nextFailureCode, nextFailureMessage, version + 1);
    }
}
