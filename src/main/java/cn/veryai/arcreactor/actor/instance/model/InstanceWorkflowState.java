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
        Map<Integer, InstanceForwardingSpec> forwardingSpecs,
        Map<Integer, PreparedForwarding> preparedForwardings,
        Map<String, DeletedForwarding> deletedUserForwardings,
        NetworkPreparation networkPreparation,
        PreparedDependencies dependencies,
        SecurityGroupPreparation securityGroupPreparation,
        boolean novaDeletionAccepted,
        List<String> remainingHypervisorIds,
        InstanceQosUpdate qosUpdate) implements Serializable {

    public record DeletedForwarding(InstanceForwardingSpec spec, PreparedForwarding resources) implements Serializable {}

    public InstanceWorkflowState {
        forwardingSpecs = Map.copyOf(forwardingSpecs);
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

    public InstanceWorkflowState forwardingCreated(InstanceForwardingSpec forwarding, PreparedForwarding resources) {
        Map<Integer, PreparedForwarding> nextPrepared = new HashMap<>(preparedForwardings);
        nextPrepared.put(forwarding.internalPort(), resources);
        return withForwarding(forwarding, nextPrepared);
    }

    public InstanceWorkflowState userForwardingDeleted(InstanceForwardingSpec forwarding) {
        String claimId = forwarding.operation().claimId();
        if (deletedUserForwardings.containsKey(claimId)) return touch();
        int port = forwarding.internalPort();
        PreparedForwarding resources = preparedForwardings.get(port);
        if (resources != null && !claimId.equals(resources.claimId())) resources = null;
        Map<String, DeletedForwarding> deleted = new HashMap<>(deletedUserForwardings);
        deleted.put(claimId, new DeletedForwarding(forwarding, resources));
        InstanceForwardingSpec current = forwardingSpecs.get(port);
        if (current == null || current.operation() == null || !claimId.equals(current.operation().claimId())) {
            return withUpdatedResources(forwardingSpecs, preparedForwardings, deleted, networkPreparation, qosUpdate);
        }
        Map<Integer, InstanceForwardingSpec> nextSpecs = new HashMap<>(forwardingSpecs);
        nextSpecs.remove(port);
        Map<Integer, PreparedForwarding> nextPrepared = preparedForwardings;
        if (resources != null) {
            nextPrepared = new HashMap<>(preparedForwardings);
            nextPrepared.remove(port);
        }
        return withUpdatedResources(nextSpecs, nextPrepared, deleted, networkPreparation, qosUpdate);
    }

    public InstanceWorkflowState forwardingChanged(InstanceForwardingSpec forwarding) {
        return withForwarding(forwarding, preparedForwardings);
    }

    private InstanceWorkflowState withForwarding(InstanceForwardingSpec forwarding, Map<Integer, PreparedForwarding> nextPrepared) {
        Map<Integer, InstanceForwardingSpec> nextSpecs = new HashMap<>(forwardingSpecs);
        nextSpecs.put(forwarding.internalPort(), forwarding);
        return withUpdatedResources(nextSpecs, nextPrepared, deletedUserForwardings, networkPreparation, qosUpdate);
    }

    public InstanceWorkflowState qosUpdateChanged(InstanceQosUpdate update) {
        return withUpdatedResources(forwardingSpecs, preparedForwardings, deletedUserForwardings, networkPreparation, update);
    }

    public InstanceWorkflowState qosUpdated(InstanceQosUpdate update, PreparedNetwork resources) {
        return withUpdatedResources(forwardingSpecs, preparedForwardings, deletedUserForwardings, new NetworkPreparation(resources), update);
    }

    private InstanceWorkflowState withUpdatedResources(Map<Integer, InstanceForwardingSpec> nextSpecs,
            Map<Integer, PreparedForwarding> nextPrepared, Map<String, DeletedForwarding> deleted,
            NetworkPreparation network, InstanceQosUpdate nextQosUpdate) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt, pollingGeneration,
                failureCode, failureMessage, version + 1, nextSpecs, nextPrepared, deleted,
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

    private Stream<InstanceForwardingSpec> requestedForwardings() {
        return forwardingSpecs.values().stream().filter(entry -> entry.operation() != null);
    }

    public List<InstanceForwardingSpec> currentUserForwardings() {
        return requestedForwardings().filter(entry -> entry.kind() == ForwardingKind.USER)
                .sorted(Comparator.comparing(entry -> entry.operation().claimId())).toList();
    }

    public InstanceForwardingSpec forwardingByClaimId(String claimId) {
        return requestedForwardings().filter(entry -> entry.operation().claimId().equals(claimId)).findFirst().orElse(null);
    }

    public InstanceForwardingSpec userForwarding(String claimId) {
        DeletedForwarding deleted = deletedUserForwardings.get(claimId);
        if (deleted != null) return deleted.spec();
        InstanceForwardingSpec current = forwardingByClaimId(claimId);
        return current != null && current.kind() == ForwardingKind.USER ? current : null;
    }

    public boolean hasPendingOperation() {
        return (qosUpdate != null && qosUpdate.phase() == InstanceQosUpdate.Phase.UPDATING)
                || requestedForwardings().map(InstanceForwardingSpec::operation).anyMatch(operation ->
                        operation.phase() == InstanceForwardingSpec.Phase.DELETING
                        || (operation.phase() == InstanceForwardingSpec.Phase.CREATING && !operation.instanceInitialization()));
    }

    /** Every accepted claim can own resources even before the instance receives its ready event. */
    public List<ForwardingDeletionTarget> forwardingsForDeletion() {
        return requestedForwardings().map(forwarding -> {
            PreparedForwarding resources = preparedForwardings.get(forwarding.internalPort());
            return resources == null ? forwarding.deletionTarget() : ForwardingDeletionTarget.from(resources);
        }).sorted(Comparator.comparing(ForwardingDeletionTarget::claimId)).toList();
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
                failureCode, failureMessage, version + 1, forwardingSpecs, preparedForwardings, deletedUserForwardings,
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
                failureCode, failureMessage, version + 1, forwardingSpecs, preparedForwardings, deletedUserForwardings,
                networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
    }

    public InstanceWorkflowState dependenciesPrepared(PreparedDependencies prepared) {
        return new InstanceWorkflowState(instanceId, userId, requestId, phase, spec, hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingSpecs, preparedForwardings, deletedUserForwardings, networkPreparation, prepared,
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
                forwardingSpecs, preparedForwardings, deletedUserForwardings, preparation, dependencies,
                securityGroupPreparation, novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
    }

    public InstanceWorkflowState created(String nextRequestId, InstanceSpec nextSpec) {
        Map<Integer, InstanceForwardingSpec> systemSpecs = new HashMap<>();
        for (int port : nextSpec.forwarding().internalPorts()) {
            systemSpecs.put(port, InstanceForwardingSpec.system(port));
        }
        return new InstanceWorkflowState(instanceId, nextSpec.share().userId(), nextRequestId,
                InstancePhase.SCHEDULING, nextSpec,
                hypervisorId, reservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, null, null, version + 1,
                systemSpecs, preparedForwardings, deletedUserForwardings, networkPreparation, dependencies, securityGroupPreparation,
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
                forwardingSpecs, preparedForwardings, deletedUserForwardings, networkPreparation,
                dependencies, securityGroupPreparation, novaDeletionAccepted, nextRemainingHypervisorIds, qosUpdate);
    }

    public InstanceWorkflowState hypervisorSelected(String nextHypervisorId, String nextReservationId) {
        return new InstanceWorkflowState(instanceId, userId, requestId, InstancePhase.RESERVING_RESOURCES, spec,
                nextHypervisorId, nextReservationId,
                novaOperationId, osNovaServerId, novaStatus, provisioningStartedAt,
                pollingGeneration, failureCode, failureMessage, version + 1,
                forwardingSpecs, preparedForwardings, deletedUserForwardings, networkPreparation,
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
                forwardingSpecs, preparedForwardings, deletedUserForwardings, networkPreparation,
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
                nextFailureCode, nextFailureMessage, version + 1, forwardingSpecs, preparedForwardings, deletedUserForwardings,
                networkPreparation, dependencies, securityGroupPreparation,
                novaDeletionAccepted, remainingHypervisorIds, qosUpdate);
    }
}
