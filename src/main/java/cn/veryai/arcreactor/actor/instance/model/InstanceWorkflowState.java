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
import java.util.stream.Stream;

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
        Map<Integer, InstanceForwardingState> forwardingStates,
        Map<Integer, PreparedForwarding> preparedForwardings,
        Map<String, DeletedForwarding> deletedUserForwardings,
        NetworkPreparation networkPreparation,
        PreparedDependencies dependencies,
        SecurityGroupPreparation securityGroupPreparation,
        boolean novaDeletionAccepted,
        List<String> remainingHypervisorIds,
        InstanceQosUpdate qosUpdate) implements Serializable {

    public record DeletedForwarding(InstanceForwardingState forwarding, PreparedForwarding resources) implements Serializable {}

    public InstanceWorkflowState {
        forwardingStates = Map.copyOf(forwardingStates);
        preparedForwardings = Map.copyOf(preparedForwardings);
        deletedUserForwardings = Map.copyOf(deletedUserForwardings);
        remainingHypervisorIds = List.copyOf(remainingHypervisorIds);
    }

    public static InstanceWorkflowState empty(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        return new InstanceWorkflowState(instanceId, null, null, InstancePhase.NEW, null, null, null,
                null, null, null, null, 0, null, null, 0, Map.of(), Map.of(), Map.of(), null, null, null,
                false, List.of(), null);
    }

    public InstanceWorkflowState forwardingCreated(InstanceForwardingState forwarding, PreparedForwarding resources) {
        Map<Integer, PreparedForwarding> nextPrepared = new HashMap<>(preparedForwardings);
        nextPrepared.put(forwarding.internalPort(), resources);
        return withForwarding(forwarding, nextPrepared);
    }

    public InstanceWorkflowState forwardingDeleted(InstanceForwardingState forwarding) {
        String claimId = forwarding.claimId();
        if (deletedUserForwardings.containsKey(claimId)) return touch();
        int port = forwarding.internalPort();
        PreparedForwarding resources = preparedForwardings.get(port);
        if (resources != null && !claimId.equals(resources.claimId())) resources = null;
        Map<String, DeletedForwarding> deleted = deletedUserForwardings;
        if (forwarding.kind() == ForwardingKind.USER) {
            deleted = new HashMap<>(deletedUserForwardings);
            deleted.put(claimId, new DeletedForwarding(forwarding, resources));
        }
        InstanceForwardingState current = forwardingStates.get(port);
        if (current == null || !claimId.equals(current.claimId())) {
            return withUpdatedResources(forwardingStates, preparedForwardings, deleted, networkPreparation, qosUpdate);
        }
        Map<Integer, InstanceForwardingState> nextForwardings = new HashMap<>(forwardingStates);
        nextForwardings.remove(port);
        Map<Integer, PreparedForwarding> nextPrepared = preparedForwardings;
        if (resources != null) {
            nextPrepared = new HashMap<>(preparedForwardings);
            nextPrepared.remove(port);
        }
        return withUpdatedResources(nextForwardings, nextPrepared, deleted, networkPreparation, qosUpdate);
    }

    public InstanceWorkflowState forwardingChanged(InstanceForwardingState forwarding) {
        return withForwarding(forwarding, preparedForwardings);
    }

    private InstanceWorkflowState withForwarding(InstanceForwardingState forwarding, Map<Integer, PreparedForwarding> nextPrepared) {
        Map<Integer, InstanceForwardingState> nextForwardings = new HashMap<>(forwardingStates);
        nextForwardings.put(forwarding.internalPort(), forwarding);
        return withUpdatedResources(nextForwardings, nextPrepared, deletedUserForwardings, networkPreparation, qosUpdate);
    }

    public InstanceWorkflowState qosUpdateChanged(InstanceQosUpdate update) {
        return withUpdatedResources(forwardingStates, preparedForwardings, deletedUserForwardings, networkPreparation, update);
    }

    public InstanceWorkflowState qosUpdated(InstanceQosUpdate update, PreparedNetwork resources) {
        return withUpdatedResources(forwardingStates, preparedForwardings, deletedUserForwardings, new NetworkPreparation(resources), update);
    }

    private InstanceWorkflowState withUpdatedResources(Map<Integer, InstanceForwardingState> nextForwardings,
            Map<Integer, PreparedForwarding> nextPrepared, Map<String, DeletedForwarding> deleted,
            NetworkPreparation network, InstanceQosUpdate nextQosUpdate) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt, pollingGeneration,
                failureCode, failureMessage, version + 1, nextForwardings, nextPrepared, deleted,
                network, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, nextQosUpdate);
    }

    public List<PreparedForwarding> visibleForwardings() {
        if (preparedForwardings.isEmpty()) return List.of();
        List<PreparedForwarding> result = new ArrayList<>(preparedForwardings.size());
        List<Integer> initialPorts = spec.forwarding().internalPorts();
        for (int port : initialPorts) {
            PreparedForwarding forwarding = preparedForwardings.get(port);
            if (forwarding != null) result.add(forwarding);
        }
        preparedForwardings.values().stream()
                .filter(forwarding -> !initialPorts.contains(forwarding.internalPort()))
                .sorted(Comparator.comparing(PreparedForwarding::claimId))
                .forEach(result::add);
        return List.copyOf(result);
    }

    public List<InstanceForwardingState> currentUserForwardings() {
        return forwardingStates.values().stream().filter(entry -> entry.kind() == ForwardingKind.USER)
                .sorted(Comparator.comparing(InstanceForwardingState::claimId)).toList();
    }

    public InstanceForwardingState forwardingByClaimId(String claimId) {
        return forwardingStates.values().stream().filter(entry -> entry.claimId().equals(claimId)).findFirst().orElse(null);
    }

    public InstanceForwardingState userForwarding(String claimId) {
        DeletedForwarding deleted = deletedUserForwardings.get(claimId);
        if (deleted != null) return deleted.forwarding();
        InstanceForwardingState current = forwardingByClaimId(claimId);
        return current != null && current.kind() == ForwardingKind.USER ? current : null;
    }

    public boolean hasPendingOperation() {
        return (qosUpdate != null && qosUpdate.phase() == InstanceQosUpdate.Phase.UPDATING)
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
                failureCode, failureMessage, version + 1, forwardingStates, preparedForwardings, deletedUserForwardings,
                networkPreparation, dependencies, preparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
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
                failureCode, failureMessage, version + 1, forwardingStates, preparedForwardings, deletedUserForwardings,
                networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
    }

    public InstanceWorkflowState dependenciesPrepared(PreparedDependencies prepared) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingStates, preparedForwardings, deletedUserForwardings, networkPreparation, prepared,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
    }

    public InstanceWorkflowState preparingForwarding() {
        return copy(requestId, InstancePhase.PREPARING_FORWARDING, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage);
    }

    public int currentForwardingPort() {
        return pendingInitializationPorts().findFirst().orElseThrow(() -> new IllegalStateException("All forwarding ports are prepared"));
    }

    private Stream<Integer> pendingInitializationPorts() {
        return spec.forwarding().internalPorts().stream()
                .filter(port -> !preparedForwardings.containsKey(port));
    }

    public boolean onlyCurrentInitializationPortPending() {
        return pendingInitializationPorts().limit(2).count() == 1;
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
                forwardingStates, preparedForwardings, deletedUserForwardings, preparation, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
    }

    public InstanceWorkflowState created(String nextRequestId, InstanceSpec nextSpec) {
        return new InstanceWorkflowState(instanceId, nextSpec.share().userId(), nextRequestId,
                InstancePhase.SCHEDULING, nextSpec,
                hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, null, null, version + 1,
                Map.of(), preparedForwardings, deletedUserForwardings, networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
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
                forwardingStates, preparedForwardings, deletedUserForwardings, networkPreparation,
                dependencies, securityGroupPreparation, novaDeletionAccepted, nextRemainingHypervisorIds, qosUpdate);
    }

    public InstanceWorkflowState hypervisorSelected(String nextHypervisorId, String nextReservationId) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.RESERVING_RESOURCES, spec,
                nextHypervisorId, nextReservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingStates, preparedForwardings, deletedUserForwardings, networkPreparation,
                dependencies, securityGroupPreparation, novaDeletionAccepted, List.of(), qosUpdate);
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
                forwardingStates, preparedForwardings, deletedUserForwardings, networkPreparation,
                dependencies, securityGroupPreparation, true, remainingHypervisorIds, qosUpdate);
    }

    public InstanceWorkflowState novaDeleted() {
        return withDeletionPhase(InstancePhase.DELETING_FORWARDINGS);
    }

    public InstanceWorkflowState forwardingsDeleted() {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.DELETING_NETWORK, spec,
                hypervisorId, reservationId, novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration + 1, failureCode, failureMessage, version + 1,
                Map.of(), Map.of(), deletedUserForwardings, networkPreparation, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
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
                nextFailureCode, nextFailureMessage, version + 1, forwardingStates, preparedForwardings, deletedUserForwardings,
                networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
    }
}
