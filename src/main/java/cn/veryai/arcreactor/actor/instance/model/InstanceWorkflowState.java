package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, InstanceForwardingState> forwardingStates,
        NetworkPreparation networkPreparation,
        PreparedDependencies dependencies,
        SecurityGroupPreparation securityGroupPreparation,
        boolean novaDeletionAccepted,
        List<String> remainingHypervisorIds,
        InstanceQosUpdate qosUpdate,
        String osImageId,
        InstanceServerOperation serverOperation) implements Serializable {

    public InstanceWorkflowState {
        forwardingStates = Map.copyOf(forwardingStates);
        remainingHypervisorIds = List.copyOf(remainingHypervisorIds);
        if (osImageId == null && spec != null) osImageId = spec.osImageId();
    }

    public static InstanceWorkflowState empty(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        return new InstanceWorkflowState(instanceId, null, null, InstancePhase.NEW, null, null, null,
                null, null, null, null, 0, null, null, 0, Map.of(), null, null, null,
                false, List.of(), null, null, null);
    }

    public InstanceWorkflowState forwardingDeleted(InstanceForwardingState forwarding) {
        String claimId = forwarding.claimId();
        if (!forwardingStates.containsKey(claimId)) return touch();
        Map<String, InstanceForwardingState> nextForwardings = new HashMap<>(forwardingStates);
        nextForwardings.remove(claimId);
        return withUpdatedResources(nextForwardings, networkPreparation, qosUpdate);
    }

    public InstanceWorkflowState forwardingChanged(InstanceForwardingState forwarding) {
        Map<String, InstanceForwardingState> nextForwardings = new HashMap<>(forwardingStates);
        nextForwardings.put(forwarding.claimId(), forwarding);
        return withUpdatedResources(nextForwardings, networkPreparation, qosUpdate);
    }

    public InstanceWorkflowState qosUpdateChanged(InstanceQosUpdate update) {
        return withUpdatedResources(forwardingStates, networkPreparation, update);
    }

    public InstanceWorkflowState qosUpdated(InstanceQosUpdate update, PreparedNetwork resources) {
        return withUpdatedResources(forwardingStates, new NetworkPreparation(resources), update);
    }

    public InstanceWorkflowState serverOperationChanged(InstanceServerOperation operation,
            OpenStackClient.NovaServer.Status status, String observedImageId, String code) {
        InstancePhase nextPhase = switch (operation.phase()) {
            case SUBMITTING, RUNNING -> operation.type().instancePhase();
            case SUCCEEDED -> InstancePhase.ACTIVE;
            case FAILED -> InstancePhase.FAILED;
        };
        String nextImageId = observedImageId == null || observedImageId.isBlank() ? osImageId : observedImageId;
        return new InstanceWorkflowState(instanceId, userId, requestId, nextPhase, spec, hypervisorId,
                reservationId, novaOperationId, osNovaServerId, status == null ? novaStatus : status,
                provisioningStartedAt, pollingGeneration + 1, code, operation.failure(), version + 1,
                forwardingStates, networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate, nextImageId, operation);
    }

    private InstanceWorkflowState withUpdatedResources(Map<String, InstanceForwardingState> nextForwardings,
            NetworkPreparation network, InstanceQosUpdate nextQosUpdate) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt, pollingGeneration,
                failureCode, failureMessage, version + 1, nextForwardings,
                network, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, nextQosUpdate, osImageId, serverOperation);
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

    public boolean hasPendingOperation() {
        return (serverOperation != null && serverOperation.inProgress())
                || (qosUpdate != null && qosUpdate.phase() == InstanceQosUpdate.Phase.UPDATING)
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
                hypervisorId, reservationId, novaOperationId, osNovaServerId, novaStatus,
                provisioningStartedAt, pollingGeneration + 1,
                failureCode, failureMessage, version + 1, forwardingStates,
                networkPreparation, dependencies, preparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate, osImageId, serverOperation);
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
                failureCode, failureMessage, version + 1, forwardingStates,
                networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate, osImageId, serverOperation);
    }

    public InstanceWorkflowState dependenciesPrepared(PreparedDependencies prepared) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingStates, networkPreparation, prepared,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, qosUpdate, osImageId, serverOperation);
    }

    public InstanceWorkflowState preparingForwarding() {
        return copy(requestId, InstancePhase.PREPARING_FORWARDING, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage);
    }

    public InstanceWorkflowState preparingNetwork() {
        return withNetwork(InstancePhase.PREPARING_NETWORK,
                new NetworkPreparation(PreparedNetwork.empty()));
    }

    public InstanceWorkflowState networkPrepared(PreparedNetwork resources) {
        return withNetwork(phase, new NetworkPreparation(resources));
    }

    private InstanceWorkflowState withNetwork(InstancePhase nextPhase, NetworkPreparation preparation) {
        return new InstanceWorkflowState(instanceId, userId, requestId, nextPhase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingStates, preparation, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, qosUpdate, osImageId, serverOperation);
    }

    public InstanceWorkflowState created(String nextRequestId, InstanceSpec nextSpec) {
        return new InstanceWorkflowState(instanceId, nextSpec.share().userId(), nextRequestId,
                InstancePhase.SCHEDULING, nextSpec,
                hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, null, null, version + 1,
                Map.of(), networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate, nextSpec.osImageId(), serverOperation);
    }

    public InstanceWorkflowState scheduling(String nextReservationId) {
        return copy(requestId, InstancePhase.SCHEDULING, spec, hypervisorId, nextReservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage);
    }

    public InstanceWorkflowState reservingResources(String nextHypervisorId,
            List<String> nextRemainingHypervisorIds) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.RESERVING_RESOURCES, spec,
                nextHypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                forwardingStates, networkPreparation,
                dependencies, securityGroupPreparation, novaDeletionAccepted, nextRemainingHypervisorIds,
                qosUpdate, osImageId, serverOperation);
    }

    public InstanceWorkflowState hypervisorSelected(String nextHypervisorId) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.RESERVING_RESOURCES, spec,
                nextHypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingStates, networkPreparation,
                dependencies, securityGroupPreparation, novaDeletionAccepted, List.of(), qosUpdate, osImageId, serverOperation);
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
                forwardingStates, networkPreparation,
                dependencies, securityGroupPreparation, true, remainingHypervisorIds, qosUpdate, osImageId, serverOperation);
    }

    public InstanceWorkflowState novaDeleted() {
        return withDeletionPhase(InstancePhase.DELETING_FORWARDINGS);
    }

    public InstanceWorkflowState forwardingsDeleted() {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.DELETING_NETWORK, spec,
                hypervisorId, reservationId, novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                Map.of(), networkPreparation, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, qosUpdate, osImageId, serverOperation);
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
                nextFailureCode, nextFailureMessage, version + 1, forwardingStates,
                networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate, osImageId, serverOperation);
    }
}
