package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import cn.veryai.arcreactor.actor.instance.InstanceOperationIds;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record InstanceWorkflowState(
        String instanceId,
        String userId,
        String requestId,
        InstancePhase phase,
        InstanceSpec spec,
        String hypervisorId,
        String reservationId,
        String novaOperationId,
        String osNovaServerId,
        OpenStackClient.NovaServer.Status novaStatus,
        Instant provisioningStartedAt,
        long pollingGeneration,
        String failureCode,
        String failureMessage,
        long version,
        boolean forwardingPreparationStarted,
        List<PreparedForwarding> preparedForwardings,
        NetworkPreparation networkPreparation,
        int forwardingCandidateIndex,
        PreparedDependencies dependencies,
        SecurityGroupPreparation securityGroupPreparation,
        boolean novaDeletionAccepted,
        List<String> remainingHypervisorIds,
        InstanceManagementState management) implements Serializable {

    public InstanceWorkflowState {
        preparedForwardings = List.copyOf(preparedForwardings);
        remainingHypervisorIds = List.copyOf(remainingHypervisorIds);
    }

    public static InstanceWorkflowState empty(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        return new InstanceWorkflowState(instanceId, null, null, InstancePhase.NEW, null, null, null,
                null, null, null, null, 0, null, null, 0, false, List.of(), null, 0, null, null, false, List.of(), InstanceManagementState.empty());
    }

    public InstanceWorkflowState userForwardingChanged(UserForwarding forwarding) {
        return withManagement(management.withForwarding(forwarding), networkPreparation);
    }

    public InstanceWorkflowState qosUpdateChanged(InstanceQosUpdate update) {
        return withManagement(management.withQos(update), networkPreparation);
    }

    public InstanceWorkflowState qosUpdated(InstanceQosUpdate update, PreparedNetwork resources) {
        return withManagement(management.withQos(update), new NetworkPreparation(resources));
    }

    private InstanceWorkflowState withManagement(InstanceManagementState next, NetworkPreparation network) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt, pollingGeneration,
                failureCode, failureMessage, version + 1, forwardingPreparationStarted, preparedForwardings,
                network, forwardingCandidateIndex, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, next);
    }

    public List<PreparedForwarding> visibleForwardings() {
        List<PreparedForwarding> result = new ArrayList<>(preparedForwardings);
        management.userForwardings().values().stream()
                .filter(item -> item.phase() != UserForwarding.Phase.DELETED && item.resources() != null)
                .sorted(java.util.Comparator.comparing(UserForwarding::forwardingId))
                .forEach(item -> result.add(item.resources()));
        return List.copyOf(result);
    }

    /** Includes failed user creations whose FIP claim may already own resources. */
    public List<PreparedForwarding> forwardingsForDeletion() {
        List<PreparedForwarding> result = new ArrayList<>(preparedForwardings);
        if (forwardingPreparationStarted && spec.forwarding() != null
                && preparedForwardings.size() < spec.forwarding().internalPorts().size()) {
            result.add(new PreparedForwarding(
                    InstanceOperationIds.forwardingClaimId(this),
                    spec.forwarding().osFloatingIpIds().get(forwardingCandidateIndex), null,
                    currentForwardingPort(), 0, null, null,
                    ForwardingKind.SYSTEM));
        }
        management.userForwardings().values().stream()
                .filter(item -> item.phase() != UserForwarding.Phase.DELETED)
                .sorted(java.util.Comparator.comparing(UserForwarding::forwardingId))
                .forEach(item -> result.add(item.deletionTarget()));
        return List.copyOf(result);
    }

    public String osProjectId() { return dependencies == null ? null : dependencies.osProjectId(); }

    public String osSecurityGroupId() {
        return securityGroupPreparation == null ? null : securityGroupPreparation.osSecurityGroupId();
    }

    public InstanceWorkflowState preparingSecurityGroup() {
        return withSecurityGroup(new SecurityGroupPreparation(null));
    }

    public InstanceWorkflowState securityGroupPrepared(String osSecurityGroupId) {
        return withSecurityGroup(new SecurityGroupPreparation(osSecurityGroupId));
    }

    private InstanceWorkflowState withSecurityGroup(SecurityGroupPreparation preparation) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.PREPARING_SECURITY_GROUP, spec,
                hypervisorId, reservationId, novaOperationId, osNovaServerId, novaStatus,
                provisioningStartedAt, pollingGeneration + 1,
                failureCode, failureMessage, version + 1, forwardingPreparationStarted, preparedForwardings,
                networkPreparation, forwardingCandidateIndex, dependencies, preparation,
                novaDeletionAccepted, remainingHypervisorIds, management);
    }

    public String shareId() {
        return dependencies == null ? null : dependencies.share().shareId();
    }

    public PreparedShare preparedShare() {
        return dependencies == null ? null : dependencies.preparedShare();
    }

    public InstanceWorkflowState preparingDependencies() {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.PREPARING_DEPENDENCIES, spec,
                hypervisorId, reservationId, novaOperationId, osNovaServerId, novaStatus,
                provisioningStartedAt, pollingGeneration + 1,
                failureCode, failureMessage, version + 1, forwardingPreparationStarted, preparedForwardings,
                networkPreparation, forwardingCandidateIndex, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, management);
    }

    public InstanceWorkflowState dependenciesPrepared(PreparedDependencies prepared) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings, networkPreparation, forwardingCandidateIndex, prepared,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, management);
    }

    public InstanceWorkflowState preparingForwarding() {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.PREPARING_FORWARDING, spec,
                hypervisorId, reservationId, novaOperationId, osNovaServerId, novaStatus,
                provisioningStartedAt, pollingGeneration + 1,
                failureCode, failureMessage, version + 1, true, List.of(), networkPreparation, 0, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, management);
    }

    public InstanceWorkflowState forwardingPrepared(PreparedForwarding forwarding) {
        if (forwarding.internalPort() != currentForwardingPort()) {
            throw new IllegalStateException("forwarding result does not match the current service port");
        }
        List<PreparedForwarding> next = new ArrayList<>(preparedForwardings);
        next.add(forwarding);
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, next, networkPreparation, 0, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, management);
    }

    public int currentForwardingPort() {
        return spec.forwarding().internalPorts().get(preparedForwardings.size());
    }

    public InstanceWorkflowState forwardingCandidateSelected(int candidateIndex) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings,
                networkPreparation, candidateIndex, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, management);
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
        return new InstanceWorkflowState(instanceId, userId, requestId, nextPhase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings, preparation, forwardingCandidateIndex, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, management);
    }

    public InstanceWorkflowState created(String nextRequestId, InstanceSpec nextSpec) {
        return new InstanceWorkflowState(instanceId, nextSpec.share().userId(), nextRequestId,
                InstancePhase.SCHEDULING, nextSpec,
                hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, null, null, version + 1, forwardingPreparationStarted,
                preparedForwardings, networkPreparation, forwardingCandidateIndex, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, management);
    }

    public InstanceWorkflowState scheduling() {
        return copy(requestId, InstancePhase.SCHEDULING, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage);
    }

    public InstanceWorkflowState reservingResources(String nextHypervisorId, String nextReservationId,
            List<String> nextRemainingHypervisorIds) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.RESERVING_RESOURCES, spec,
                nextHypervisorId, nextReservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings, networkPreparation, forwardingCandidateIndex,
                dependencies, securityGroupPreparation, novaDeletionAccepted, nextRemainingHypervisorIds, management);
    }

    public InstanceWorkflowState hypervisorSelected(String nextHypervisorId, String nextReservationId) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.RESERVING_RESOURCES, spec,
                nextHypervisorId, nextReservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings, networkPreparation, forwardingCandidateIndex,
                dependencies, securityGroupPreparation, novaDeletionAccepted, List.of(), management);
    }

    public InstanceWorkflowState provisioning(String operationId, Instant startedAt) {
        return copy(requestId, InstancePhase.PROVISIONING, spec, hypervisorId, reservationId,
                operationId, osNovaServerId, novaStatus, startedAt,
                pollingGeneration, failureCode, failureMessage);
    }

    public InstanceWorkflowState serverObserved(String osServerId, OpenStackClient.NovaServer.Status status) {
        return copy(requestId, InstancePhase.PROVISIONING, spec, hypervisorId, reservationId,
                novaOperationId, osServerId, status, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage);
    }

    public InstanceWorkflowState checkingSsh() {
        return copy(requestId, InstancePhase.CHECKING_SSH, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, OpenStackClient.NovaServer.Status.ACTIVE,
                provisioningStartedAt, pollingGeneration + 1,
                failureCode, failureMessage);
    }

    public InstanceWorkflowState confirming() {
        return copy(requestId, InstancePhase.CONFIRMING, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, OpenStackClient.NovaServer.Status.ACTIVE,
                provisioningStartedAt, pollingGeneration + 1,
                failureCode, failureMessage);
    }

    public InstanceWorkflowState deleting() {
        return withDeletionPhase(InstancePhase.DELETING_NOVA);
    }

    public InstanceWorkflowState novaDeletionAccepted(String osServerId) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.DELETING_NOVA, spec,
                hypervisorId, reservationId,
                novaOperationId, osServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingPreparationStarted, preparedForwardings, networkPreparation, forwardingCandidateIndex,
                dependencies, securityGroupPreparation, true, remainingHypervisorIds, management);
    }

    public InstanceWorkflowState novaDeleted() {
        return withDeletionPhase(InstancePhase.DELETING_FORWARDINGS);
    }

    public InstanceWorkflowState forwardingsDeleted() {
        return withDeletionPhase(InstancePhase.DELETING_NETWORK);
    }

    public InstanceWorkflowState networkDeleted() {
        return withDeletionPhase(InstancePhase.DELETING_SECURITY_GROUP);
    }

    public InstanceWorkflowState securityGroupDeleted() {
        return withDeletionPhase(InstancePhase.CANCELLING_RESERVATION);
    }

    public InstanceWorkflowState reservationCancelled() {
        return withDeletionPhase(InstancePhase.RELEASING_ALLOCATION);
    }

    public InstanceWorkflowState deleted() {
        return withDeletionPhase(InstancePhase.DELETED);
    }

    private InstanceWorkflowState withDeletionPhase(InstancePhase nextPhase) {
        return copy(requestId, nextPhase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage);
    }

    public InstanceWorkflowState active() {
        return copy(requestId, InstancePhase.ACTIVE, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, OpenStackClient.NovaServer.Status.ACTIVE,
                provisioningStartedAt, pollingGeneration,
                null, null);
    }

    public InstanceWorkflowState failed(String code, String message) {
        return copy(requestId, InstancePhase.FAILED, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, code, message);
    }

    public InstanceWorkflowState touch() {
        return copy(requestId, phase, spec, hypervisorId, reservationId, novaOperationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage);
    }

    private InstanceWorkflowState copy(String nextRequestId, InstancePhase nextPhase, InstanceSpec nextSpec,
            String nextHypervisorId, String nextReservationId, String nextOperationId,
            String nextServerId, OpenStackClient.NovaServer.Status nextNovaStatus, Instant nextStartedAt,
            long nextGeneration, String nextFailureCode,
            String nextFailureMessage) {
        return new InstanceWorkflowState(instanceId, userId, nextRequestId, nextPhase, nextSpec,
                nextHypervisorId, nextReservationId, nextOperationId, nextServerId, nextNovaStatus,
                nextStartedAt, nextGeneration,
                nextFailureCode, nextFailureMessage, version + 1, forwardingPreparationStarted, preparedForwardings,
                networkPreparation, forwardingCandidateIndex, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, management);
    }
}
