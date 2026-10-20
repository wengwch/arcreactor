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
        Instant sharePreparationDeadline,
        PreparedShare preparedShare,
        Instant forwardingPreparationDeadline,
        PreparedForwarding preparedForwarding,
        NetworkPreparation networkPreparation,
        ProjectPreparation projectPreparation) implements Serializable {

    public static InstanceWorkflowState empty(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        return new InstanceWorkflowState(instanceId, null, InstancePhase.NEW, null, null, null,
                null, null, null, null, null, 0, 0, null, null, 0, null, null, null, null, null, null);
    }

    public InstanceWorkflowState preparingProject(Instant deadline) {
        return withProject(InstancePhase.PREPARING_PROJECT, new ProjectPreparation(deadline, null));
    }

    public InstanceWorkflowState projectPrepared(String projectId) {
        return withProject(phase, new ProjectPreparation(projectPreparation.deadline(), projectId));
    }

    private InstanceWorkflowState withProject(InstancePhase nextPhase, ProjectPreparation preparation) {
        return new InstanceWorkflowState(instanceId, requestId, nextPhase, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage, version + 1,
                sharePreparationDeadline, preparedShare, forwardingPreparationDeadline, preparedForwarding,
                networkPreparation, preparation);
    }

    public String projectId() {
        return projectPreparation != null && projectPreparation.projectId() != null
                ? projectPreparation.projectId() : spec == null ? null : spec.projectId();
    }

    public ShareSpec shareSpec() {
        return spec.share().spec().withProjectId(projectId());
    }

    public InstanceWorkflowState preparingShare(Instant deadline) {
        return new InstanceWorkflowState(instanceId, requestId, InstancePhase.PREPARING_SHARE, spec,
                hypervisorId, reservationId, novaOperationId, novaServerId, novaStatus,
                provisioningStartedAt, provisioningDeadline, pollingGeneration + 1,
                schedulingAttempts, failureCode, failureMessage, version + 1, deadline, null,
                forwardingPreparationDeadline, preparedForwarding, networkPreparation, projectPreparation);
    }

    public InstanceWorkflowState dependencyCheckStarted() {
        return copy(requestId, phase, spec, hypervisorId, reservationId, novaOperationId,
                novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState sharePrepared(PreparedShare share) {
        return new InstanceWorkflowState(instanceId, requestId, phase, spec, hypervisorId,
                reservationId, novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                provisioningDeadline, pollingGeneration, schedulingAttempts, failureCode,
                failureMessage, version + 1, sharePreparationDeadline, share,
                forwardingPreparationDeadline, preparedForwarding, networkPreparation, projectPreparation);
    }

    public InstanceWorkflowState preparingForwarding(Instant deadline) {
        return new InstanceWorkflowState(instanceId, requestId, InstancePhase.PREPARING_FORWARDING, spec,
                hypervisorId, reservationId, novaOperationId, novaServerId, novaStatus,
                provisioningStartedAt, provisioningDeadline, pollingGeneration + 1, schedulingAttempts,
                failureCode, failureMessage, version + 1, sharePreparationDeadline, preparedShare, deadline, null, networkPreparation, projectPreparation);
    }

    public InstanceWorkflowState forwardingPrepared(PreparedForwarding forwarding) {
        return new InstanceWorkflowState(instanceId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage, version + 1,
                sharePreparationDeadline, preparedShare, forwardingPreparationDeadline, forwarding, networkPreparation, projectPreparation);
    }

    public InstanceWorkflowState preparingNetwork(Instant deadline, NetworkPreparation.Step firstStep) {
        return withNetwork(InstancePhase.PREPARING_NETWORK, new NetworkPreparation(deadline,
                firstStep, false, PreparedNetwork.empty()));
    }

    public InstanceWorkflowState networkRequested() {
        return withNetwork(phase, networkPreparation.requested());
    }

    public InstanceWorkflowState networkPrepared(NetworkPreparation preparation) {
        return withNetwork(phase, preparation);
    }

    private InstanceWorkflowState withNetwork(InstancePhase nextPhase, NetworkPreparation preparation) {
        return new InstanceWorkflowState(instanceId, requestId, nextPhase, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage, version + 1,
                sharePreparationDeadline, preparedShare, forwardingPreparationDeadline, preparedForwarding, preparation, projectPreparation);
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

    public InstanceWorkflowState building(String serverId, OpenStackClient.NovaServer.Status status) {
        return copy(requestId, InstancePhase.BUILDING, spec, hypervisorId, reservationId,
                novaOperationId, serverId, status, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState reconciling() {
        return copy(requestId, InstancePhase.RECONCILING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt, provisioningDeadline,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState novaStatus(OpenStackClient.NovaServer.Status status) {
        return copy(requestId, phase, spec, hypervisorId, reservationId, novaOperationId,
                novaServerId, status, provisioningStartedAt, provisioningDeadline,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage);
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
                nextFailureCode, nextFailureMessage, version + 1, sharePreparationDeadline, preparedShare,
                forwardingPreparationDeadline, preparedForwarding, networkPreparation, projectPreparation);
    }
}
