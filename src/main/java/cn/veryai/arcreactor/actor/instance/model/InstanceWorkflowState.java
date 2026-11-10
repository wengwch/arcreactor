package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
        long pollingGeneration,
        int schedulingAttempts,
        String failureCode,
        String failureMessage,
        long version,
        boolean forwardingPreparationStarted,
        List<PreparedForwarding> preparedForwardings,
        NetworkPreparation networkPreparation,
        int forwardingCandidateIndex,
        PreparedDependencies dependencies,
        SecurityGroupPreparation securityGroupPreparation,
        boolean novaDeletionAccepted) implements Serializable {

    public InstanceWorkflowState {
        preparedForwardings = List.copyOf(preparedForwardings);
    }

    public static InstanceWorkflowState empty(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        return new InstanceWorkflowState(instanceId, null, InstancePhase.NEW, null, null, null,
                null, null, null, null, 0, 0, null, null, 0, false, List.of(), null, 0, null, null, false);
    }

    public String projectId() { return dependencies == null ? null : dependencies.projectId(); }

    public String securityGroupId() {
        return securityGroupPreparation == null ? null : securityGroupPreparation.securityGroupId();
    }

    public InstanceWorkflowState preparingSecurityGroup() {
        return withSecurityGroup(new SecurityGroupPreparation(null));
    }

    public InstanceWorkflowState securityGroupPrepared(String groupId) {
        return withSecurityGroup(new SecurityGroupPreparation(groupId));
    }

    private InstanceWorkflowState withSecurityGroup(SecurityGroupPreparation preparation) {
        return new InstanceWorkflowState(instanceId, requestId, InstancePhase.PREPARING_SECURITY_GROUP, spec,
                hypervisorId, reservationId, novaOperationId, novaServerId, novaStatus,
                provisioningStartedAt, pollingGeneration + 1, schedulingAttempts,
                failureCode, failureMessage, version + 1, forwardingPreparationStarted, preparedForwardings,
                networkPreparation, forwardingCandidateIndex, dependencies, preparation,
                novaDeletionAccepted);
    }

    public String shareId() {
        return dependencies != null ? dependencies.share().shareId() : spec == null ? null : spec.share().shareId();
    }

    public PreparedShare preparedShare() {
        return dependencies == null ? null : dependencies.preparedShare();
    }

    public ShareSpec shareSpec() {
        return dependencies == null ? spec.share().spec() : dependencies.share().spec();
    }

    public InstanceWorkflowState preparingDependencies() {
        return new InstanceWorkflowState(instanceId, requestId, InstancePhase.PREPARING_DEPENDENCIES, spec,
                hypervisorId, reservationId, novaOperationId, novaServerId, novaStatus,
                provisioningStartedAt, pollingGeneration + 1, schedulingAttempts,
                failureCode, failureMessage, version + 1, forwardingPreparationStarted, preparedForwardings,
                networkPreparation, forwardingCandidateIndex, dependencies, securityGroupPreparation,
                novaDeletionAccepted);
    }

    public InstanceWorkflowState dependenciesPrepared(PreparedDependencies prepared) {
        return new InstanceWorkflowState(instanceId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings, networkPreparation, forwardingCandidateIndex, prepared,
                securityGroupPreparation, novaDeletionAccepted);
    }

    public InstanceWorkflowState preparingForwarding() {
        return new InstanceWorkflowState(instanceId, requestId, InstancePhase.PREPARING_FORWARDING, spec,
                hypervisorId, reservationId, novaOperationId, novaServerId, novaStatus,
                provisioningStartedAt, pollingGeneration + 1, schedulingAttempts,
                failureCode, failureMessage, version + 1, true, List.of(), networkPreparation, 0, dependencies,
                securityGroupPreparation, novaDeletionAccepted);
    }

    public InstanceWorkflowState forwardingPrepared(PreparedForwarding forwarding) {
        if (forwarding.internalPort() != currentForwardingPort()) {
            throw new IllegalStateException("forwarding result does not match the current service port");
        }
        List<PreparedForwarding> next = new ArrayList<>(preparedForwardings);
        next.add(forwarding);
        return new InstanceWorkflowState(instanceId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, next, networkPreparation, 0, dependencies,
                securityGroupPreparation, novaDeletionAccepted);
    }

    public int currentForwardingPort() {
        return spec.forwarding().internalPorts().get(preparedForwardings.size());
    }

    public InstanceWorkflowState forwardingCandidateSelected(int candidateIndex) {
        return new InstanceWorkflowState(instanceId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings,
                networkPreparation, candidateIndex, dependencies, securityGroupPreparation,
                novaDeletionAccepted);
    }

    public InstanceWorkflowState preparingParentPort() {
        return withNetwork(InstancePhase.PREPARING_PARENT_PORT,
                new NetworkPreparation(PreparedNetwork.empty()));
    }

    public InstanceWorkflowState preparingSubPort() {
        return withNetwork(InstancePhase.PREPARING_SUB_PORT, networkPreparation);
    }

    public InstanceWorkflowState preparingTrunk() {
        return withNetwork(InstancePhase.PREPARING_TRUNK, networkPreparation);
    }

    public InstanceWorkflowState networkPrepared(PreparedNetwork resources) {
        return withNetwork(phase, new NetworkPreparation(resources));
    }

    private InstanceWorkflowState withNetwork(InstancePhase nextPhase, NetworkPreparation preparation) {
        return new InstanceWorkflowState(instanceId, requestId, nextPhase, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings, preparation, forwardingCandidateIndex, dependencies,
                securityGroupPreparation, novaDeletionAccepted);
    }

    public InstanceWorkflowState created(String nextRequestId, InstanceSpec nextSpec) {
        return new InstanceWorkflowState(instanceId, nextRequestId, InstancePhase.SCHEDULING, nextSpec,
                hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, schedulingAttempts, null, null, version + 1, forwardingPreparationStarted,
                preparedForwardings, networkPreparation, forwardingCandidateIndex, dependencies, securityGroupPreparation,
                novaDeletionAccepted);
    }

    public InstanceWorkflowState scheduling(int attempts) {
        return copy(requestId, InstancePhase.SCHEDULING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, attempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState hypervisorSelected(String nextHypervisorId, String nextReservationId,
            int attempts) {
        return copy(requestId, InstancePhase.SCHEDULING, spec, nextHypervisorId, nextReservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, attempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState provisioning(String operationId, Instant startedAt) {
        return copy(requestId, InstancePhase.PROVISIONING, spec, hypervisorId, reservationId,
                operationId, novaServerId, novaStatus, startedAt,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState serverObserved(String serverId, OpenStackClient.NovaServer.Status status) {
        return copy(requestId, InstancePhase.PROVISIONING, spec, hypervisorId, reservationId,
                novaOperationId, serverId, status, provisioningStartedAt,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState confirming() {
        return copy(requestId, InstancePhase.CONFIRMING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, OpenStackClient.NovaServer.Status.ACTIVE,
                provisioningStartedAt, pollingGeneration + 1,
                schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState deleting() {
        return copy(requestId, InstancePhase.DELETING, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState novaDeletionAccepted(String serverId) {
        return new InstanceWorkflowState(instanceId, requestId, InstancePhase.DELETING, spec, hypervisorId, reservationId,
                novaOperationId, serverId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings, networkPreparation, forwardingCandidateIndex,
                dependencies, securityGroupPreparation, true);
    }

    public InstanceWorkflowState deleted() {
        return copy(requestId, InstancePhase.DELETED, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, schedulingAttempts, failureCode, failureMessage);
    }

    public InstanceWorkflowState active() {
        return copy(requestId, InstancePhase.ACTIVE, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, OpenStackClient.NovaServer.Status.ACTIVE,
                provisioningStartedAt, pollingGeneration,
                schedulingAttempts, null, null);
    }

    public InstanceWorkflowState failed(String code, String message) {
        return copy(requestId, InstancePhase.FAILED, spec, hypervisorId, reservationId,
                novaOperationId, novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, schedulingAttempts, code, message);
    }

    public InstanceWorkflowState touch() {
        return copy(requestId, phase, spec, hypervisorId, reservationId, novaOperationId,
                novaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, schedulingAttempts, failureCode, failureMessage);
    }

    private InstanceWorkflowState copy(String nextRequestId, InstancePhase nextPhase, InstanceSpec nextSpec,
            String nextHypervisorId, String nextReservationId, String nextOperationId,
            String nextServerId, OpenStackClient.NovaServer.Status nextNovaStatus, Instant nextStartedAt,
            long nextGeneration, int nextAttempts, String nextFailureCode,
            String nextFailureMessage) {
        return new InstanceWorkflowState(instanceId, nextRequestId, nextPhase, nextSpec,
                nextHypervisorId, nextReservationId, nextOperationId, nextServerId, nextNovaStatus,
                nextStartedAt, nextGeneration, nextAttempts,
                nextFailureCode, nextFailureMessage, version + 1, forwardingPreparationStarted, preparedForwardings,
                networkPreparation, forwardingCandidateIndex, dependencies, securityGroupPreparation,
                novaDeletionAccepted);
    }
}
