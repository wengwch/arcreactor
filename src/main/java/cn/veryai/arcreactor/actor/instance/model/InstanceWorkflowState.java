package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record InstanceWorkflowState(
        String instanceId,
        String userId,
        String requestId,
        InstancePhase phase,
        InstanceSpec spec,
        String hypervisorId,
        String hostname,
        String reservationId,
        String osNovaServerId,
        OpenStackClient.NovaServer.Status novaStatus,
        Instant provisioningStartedAt,
        long pollingGeneration,
        String failureCode,
        String failureMessage,
        long version,
        Map<String, InstanceForwardingState> forwardingStates,
        NetworkPreparation networkPreparation,
        PreparedDependencies dependencies,
        SecurityGroupPreparation securityGroupPreparation,
        boolean novaDeletionAccepted,
        List<HypervisorPlacement.Candidate> remainingHypervisors,
        InstanceQosUpdate activeQos, // Current activated record, including its expiry progress or failure.
        InstanceQosUpdate pendingQos, // In-progress or failed new update, cleared after successful activation.
        String osImageId,
        InstanceServerOperation serverOperation,
        Instant failedAt,
        Instant deletedAt) implements Serializable {

    public InstanceWorkflowState {
        forwardingStates = Map.copyOf(forwardingStates);
        remainingHypervisors = List.copyOf(remainingHypervisors);
        if (osImageId == null && spec != null) osImageId = spec.osImageId();
    }

    public static InstanceWorkflowState empty(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        return new InstanceWorkflowState(instanceId, null, null, InstancePhase.NEW, null, null, null,
                null, null, null, null, 0, null, null, 0, Map.of(), null, null, null,
                false, List.of(), null, null, null, null, null, null);
    }

    public InstanceWorkflowState forwardingDeleted(InstanceForwardingState forwarding) {
        String claimId = forwarding.claimId();
        if (!forwardingStates.containsKey(claimId)) return touch();
        Map<String, InstanceForwardingState> nextForwardings = new HashMap<>(forwardingStates);
        nextForwardings.remove(claimId);
        return withUpdatedResources(nextForwardings, networkPreparation, activeQos, pendingQos);
    }

    public InstanceWorkflowState forwardingChanged(InstanceForwardingState forwarding) {
        Map<String, InstanceForwardingState> nextForwardings = new HashMap<>(forwardingStates);
        nextForwardings.put(forwarding.claimId(), forwarding);
        return withUpdatedResources(nextForwardings, networkPreparation, activeQos, pendingQos);
    }

    public InstanceWorkflowState pendingQosChanged(InstanceQosUpdate update) {
        return withUpdatedResources(forwardingStates, networkPreparation, activeQos, update);
    }

    public InstanceWorkflowState activeQosChanged(InstanceQosUpdate update) {
        return withUpdatedResources(forwardingStates, networkPreparation, update, pendingQos);
    }

    public InstanceWorkflowState qosUpdated(InstanceQosUpdate update, PreparedNetwork resources) {
        return withUpdatedResources(forwardingStates, new NetworkPreparation(resources), update, null);
    }

    public InstanceWorkflowState qosExpired(PreparedNetwork resources) {
        return withUpdatedResources(forwardingStates, new NetworkPreparation(resources), null, pendingQos);
    }

    public InstanceWorkflowState serverOperationChanged(InstanceServerOperation operation,
            OpenStackClient.NovaServer.Status status, String observedImageId, String code, Instant failureTime) {
        InstancePhase nextPhase = switch (operation.phase()) {
            case SUBMITTING, RUNNING -> operation.type().instancePhase();
            case SUCCEEDED -> InstancePhase.ACTIVE;
            case FAILED -> InstancePhase.FAILED;
        };
        Instant nextFailedAt = switch (operation.phase()) {
            case SUBMITTING, RUNNING -> failedAt;
            case SUCCEEDED -> null;
            case FAILED -> failedAt == null ? failureTime : failedAt;
        };
        String nextImageId = observedImageId == null || observedImageId.isBlank() ? osImageId : observedImageId;
        return new InstanceWorkflowState(instanceId, userId, requestId, nextPhase, spec, hypervisorId, hostname,
                reservationId, osNovaServerId, status == null ? novaStatus : status,
                provisioningStartedAt, pollingGeneration + 1, code, operation.failure(), version + 1,
                forwardingStates, networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisors, activeQos, pendingQos, nextImageId, operation,
                nextFailedAt, deletedAt);
    }

    private InstanceWorkflowState withUpdatedResources(Map<String, InstanceForwardingState> nextForwardings,
            NetworkPreparation network, InstanceQosUpdate nextActiveQos, InstanceQosUpdate nextPendingQos) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, hostname, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt, pollingGeneration,
                failureCode, failureMessage, version + 1, nextForwardings,
                network, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisors, nextActiveQos, nextPendingQos, osImageId, serverOperation,
                failedAt, deletedAt);
    }

    public List<InstanceForwardingState> visibleForwardings() {
        if (forwardingStates.isEmpty()) return List.of();
        List<InstanceForwardingState> result = new ArrayList<>(forwardingStates.size());
        List<Integer> initialPorts = spec.forwarding().internalPorts();
        for (int port : initialPorts) {
            InstanceForwardingState forwarding = forwardingByInternalPort(port);
            if (forwarding != null && forwarding.resources() != null) result.add(forwarding);
        }
        forwardingStates.values().stream()
                .filter(forwarding -> forwarding.resources() != null)
                .filter(forwarding -> !initialPorts.contains(forwarding.internalPort()))
                .sorted(Comparator.comparing(InstanceForwardingState::claimId))
                .forEach(result::add);
        return List.copyOf(result);
    }

    public List<InstanceForwardingState> currentUserForwardings() {
        return forwardingStates.values().stream().filter(entry -> entry.kind() == ForwardingKind.USER)
                .sorted(Comparator.comparing(InstanceForwardingState::claimId)).toList();
    }

    public InstanceForwardingState forwardingByClaimId(String claimId) {
        return forwardingStates.get(claimId);
    }

    public InstanceForwardingState forwardingByInternalPort(int internalPort) {
        return forwardingStates.values().stream().filter(entry -> entry.internalPort() == internalPort).findFirst().orElse(null);
    }

    public InstanceForwardingResources forwardingResourcesByInternalPort(int internalPort) {
        InstanceForwardingState forwarding = forwardingByInternalPort(internalPort);
        return forwarding == null ? null : forwarding.resources();
    }

    public InstanceQosUpdate inProgressQos() {
        if (pendingQos != null && pendingQos.phase() == InstanceQosUpdate.Phase.UPDATING) return pendingQos;
        if (activeQos != null && activeQos.phase() == InstanceQosUpdate.Phase.EXPIRING) return activeQos;
        return null;
    }

    public boolean hasPendingOperation() {
        return (serverOperation != null && serverOperation.inProgress())
                || inProgressQos() != null
                || forwardingStates.values().stream().anyMatch(forwarding ->
                        forwarding.phase() == InstanceForwardingState.Phase.DELETING
                        || (forwarding.phase() == InstanceForwardingState.Phase.CREATING && forwarding.kind() == ForwardingKind.USER));
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
                hypervisorId, hostname, reservationId, osNovaServerId, novaStatus,
                provisioningStartedAt, pollingGeneration + 1,
                failureCode, failureMessage, version + 1, forwardingStates,
                networkPreparation, dependencies, preparation,
                novaDeletionAccepted, remainingHypervisors, activeQos, pendingQos, osImageId, serverOperation,
                failedAt, deletedAt);
    }

    public String shareId() {
        return dependencies == null ? null : dependencies.share().shareId();
    }

    public PreparedShare preparedShare() {
        return dependencies == null ? null : dependencies.preparedShare();
    }

    public InstanceWorkflowState preparingDependencies() {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.PREPARING_DEPENDENCIES, spec,
                hypervisorId, hostname, reservationId, osNovaServerId, novaStatus,
                provisioningStartedAt, pollingGeneration + 1,
                failureCode, failureMessage, version + 1, forwardingStates,
                networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisors, activeQos, pendingQos, osImageId, serverOperation,
                failedAt, deletedAt);
    }

    public InstanceWorkflowState dependenciesPrepared(PreparedDependencies prepared) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, hostname, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingStates, networkPreparation, prepared,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisors, activeQos, pendingQos, osImageId, serverOperation,
                failedAt, deletedAt);
    }

    public InstanceWorkflowState preparingForwarding() {
        return copy(requestId, InstancePhase.PREPARING_FORWARDING, spec, hypervisorId, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, failedAt, deletedAt);
    }

    public InstanceWorkflowState preparingNetwork() {
        return withNetwork(InstancePhase.PREPARING_NETWORK,
                new NetworkPreparation(PreparedNetwork.empty()));
    }

    public InstanceWorkflowState networkPrepared(PreparedNetwork resources) {
        return withNetwork(phase, new NetworkPreparation(resources));
    }

    private InstanceWorkflowState withNetwork(InstancePhase nextPhase, NetworkPreparation preparation) {
        return new InstanceWorkflowState(instanceId, userId, requestId, nextPhase, spec, hypervisorId, hostname, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingStates, preparation, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisors, activeQos, pendingQos, osImageId, serverOperation,
                failedAt, deletedAt);
    }

    public InstanceWorkflowState created(String nextRequestId, InstanceSpec nextSpec) {
        return new InstanceWorkflowState(instanceId, nextSpec.share().userId(), nextRequestId,
                InstancePhase.SCHEDULING, nextSpec,
                hypervisorId, hostname, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, null, null, version + 1,
                Map.of(), networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisors, activeQos, pendingQos, nextSpec.osImageId(), serverOperation,
                null, null);
    }

    public InstanceWorkflowState scheduling(String nextReservationId) {
        return copy(requestId, InstancePhase.SCHEDULING, spec, hypervisorId, nextReservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, failedAt, deletedAt);
    }

    public InstanceWorkflowState reservingResources(String nextHypervisorId, String nextHostname,
            List<HypervisorPlacement.Candidate> nextRemainingHypervisors) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.RESERVING_RESOURCES, spec,
                nextHypervisorId, nextHostname, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingStates, networkPreparation,
                dependencies, securityGroupPreparation, novaDeletionAccepted, nextRemainingHypervisors,
                activeQos, pendingQos, osImageId, serverOperation, failedAt, deletedAt);
    }

    public InstanceWorkflowState hypervisorSelected(String nextHypervisorId, String nextHostname) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.RESERVING_RESOURCES, spec,
                nextHypervisorId, nextHostname, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingStates, networkPreparation,
                dependencies, securityGroupPreparation, novaDeletionAccepted, List.of(), activeQos, pendingQos, osImageId, serverOperation,
                failedAt, deletedAt);
    }

    public InstanceWorkflowState provisioning(Instant startedAt) {
        Objects.requireNonNull(startedAt, "startedAt");
        return copy(requestId, InstancePhase.PROVISIONING, spec, hypervisorId, reservationId,
                osNovaServerId, novaStatus, startedAt,
                pollingGeneration, failureCode, failureMessage, failedAt, deletedAt);
    }

    public InstanceWorkflowState serverObserved(String osServerId, OpenStackClient.NovaServer.Status status) {
        return copy(requestId, InstancePhase.PROVISIONING, spec, hypervisorId, reservationId,
                osServerId, status, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, failedAt, deletedAt);
    }

    public InstanceWorkflowState confirming() {
        return copy(requestId, InstancePhase.CONFIRMING, spec, hypervisorId, reservationId,
                osNovaServerId, OpenStackClient.NovaServer.Status.ACTIVE,
                provisioningStartedAt, pollingGeneration + 1,
                failureCode, failureMessage, failedAt, deletedAt);
    }

    public InstanceWorkflowState deleting() {
        // A new persisted deletion intent may resubmit Nova deletion; recovery keeps the saved flag.
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.DELETING_NOVA, spec,
                hypervisorId, hostname, reservationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingStates, networkPreparation, dependencies, securityGroupPreparation,
                false, remainingHypervisors, activeQos, pendingQos, osImageId, serverOperation,
                failedAt, deletedAt);
    }

    public InstanceWorkflowState novaDeletionAccepted(String osServerId) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.DELETING_NOVA, spec,
                hypervisorId, hostname, reservationId,
                osServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingStates, networkPreparation,
                dependencies, securityGroupPreparation, true, remainingHypervisors, activeQos, pendingQos, osImageId, serverOperation,
                failedAt, deletedAt);
    }

    public InstanceWorkflowState novaDeleted() {
        return withDeletionPhase(InstancePhase.DELETING_FORWARDINGS);
    }

    public InstanceWorkflowState forwardingsDeleted() {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.DELETING_NETWORK, spec,
                hypervisorId, hostname, reservationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                Map.of(), networkPreparation, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisors, activeQos, pendingQos, osImageId, serverOperation,
                failedAt, deletedAt);
    }

    public InstanceWorkflowState networkDeleted() {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.DELETING_SECURITY_GROUP, spec,
                hypervisorId, hostname, reservationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingStates, networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisors, null, null, osImageId, serverOperation, failedAt, deletedAt);
    }

    public InstanceWorkflowState securityGroupDeleted() {
        return withDeletionPhase(InstancePhase.CANCELLING_RESERVATION);
    }

    public InstanceWorkflowState reservationCancelled() {
        return withDeletionPhase(InstancePhase.RELEASING_ALLOCATION);
    }

    public InstanceWorkflowState deleted(Instant deletionTime) {
        return copy(requestId, InstancePhase.DELETED, spec, hypervisorId, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, failedAt, deletionTime);
    }

    private InstanceWorkflowState withDeletionPhase(InstancePhase nextPhase) {
        return copy(requestId, nextPhase, spec, hypervisorId, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, failedAt, deletedAt);
    }

    public InstanceWorkflowState active() {
        return copy(requestId, InstancePhase.ACTIVE, spec, hypervisorId, reservationId,
                osNovaServerId, OpenStackClient.NovaServer.Status.ACTIVE,
                provisioningStartedAt, pollingGeneration,
                null, null, null, deletedAt);
    }

    public InstanceWorkflowState failed(String code, String message, Instant failureTime) {
        return copy(requestId, InstancePhase.FAILED, spec, hypervisorId, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, code, message, failedAt == null ? failureTime : failedAt, deletedAt);
    }

    public InstanceWorkflowState deletionFailed(String code, String message, Instant failureTime) {
        return copy(requestId, InstancePhase.DELETE_FAILED, spec, hypervisorId, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, code, message, failedAt == null ? failureTime : failedAt, deletedAt);
    }

    public InstanceWorkflowState touch() {
        return copy(requestId, phase, spec, hypervisorId, reservationId,
                osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, failedAt, deletedAt);
    }

    private InstanceWorkflowState copy(String nextRequestId, InstancePhase nextPhase, InstanceSpec nextSpec,
            String nextHypervisorId, String nextReservationId,
            String nextServerId, OpenStackClient.NovaServer.Status nextNovaStatus, Instant nextStartedAt,
            long nextGeneration, String nextFailureCode,
            String nextFailureMessage, Instant nextFailedAt, Instant nextDeletedAt) {
        return new InstanceWorkflowState(instanceId, userId, nextRequestId, nextPhase, nextSpec,
                nextHypervisorId, hostname, nextReservationId, nextServerId, nextNovaStatus,
                nextStartedAt, nextGeneration,
                nextFailureCode, nextFailureMessage, version + 1, forwardingStates,
                networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisors, activeQos, pendingQos, osImageId, serverOperation,
                nextFailedAt, nextDeletedAt);
    }
}
