package cn.veryai.arcreactor.actor.instance.projection;

import cn.veryai.arcreactor.actor.instance.event.DependenciesPrepared;
import cn.veryai.arcreactor.actor.instance.event.DependencyPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.HypervisorReservationStarted;
import cn.veryai.arcreactor.actor.instance.event.HypervisorSelected;
import cn.veryai.arcreactor.actor.instance.event.InstanceActivated;
import cn.veryai.arcreactor.actor.instance.event.InstanceCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceDeletionFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceDeletionStarted;
import cn.veryai.arcreactor.actor.instance.event.InstanceDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceForwardingEvent;
import cn.veryai.arcreactor.actor.instance.event.InstanceForwardingsDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceNetworkDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceProvisioningFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdated;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosExpired;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosExpiryFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosExpiryRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationSubmitted;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationCompleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceReservationCancelled;
import cn.veryai.arcreactor.actor.instance.event.InstanceSecurityGroupDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.event.NetworkPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.NetworkPrepared;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationStarted;
import cn.veryai.arcreactor.actor.instance.event.NovaDeletionAccepted;
import cn.veryai.arcreactor.actor.instance.event.NovaServerCreated;
import cn.veryai.arcreactor.actor.instance.event.NovaServerDeleted;
import cn.veryai.arcreactor.actor.instance.event.ReservationConfirmedForInstance;
import cn.veryai.arcreactor.actor.instance.event.SchedulingStarted;
import cn.veryai.arcreactor.actor.instance.event.SecurityGroupPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.SecurityGroupPrepared;
import cn.veryai.arcreactor.actor.instance.event.SshConnectivityVerified;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.projection.mybatis.InstanceProjectionMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

public final class InstanceProjectionHandler
        extends JdbcHandler<EventEnvelope<InstanceWorkflowEvent>, JdbcProjectionSession> {
    @Override
    public void process(JdbcProjectionSession session,
            EventEnvelope<InstanceWorkflowEvent> envelope) {
        InstanceProjectionMapper mapper = session.mapper(InstanceProjectionMapper.class);
        InstanceWorkflowEvent event = envelope.event();
        long version = envelope.sequenceNr();
        Timestamp timestamp = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));
        String instanceId = entityId(envelope.persistenceId());

        if (event instanceof InstanceCreationRequested created) {
            mapper.upsertCreated(instanceId, created.requestId(), created.spec(), InstancePhase.SCHEDULING, version,
                    Timestamp.from(created.requestedAt()), timestamp);
        } else if (event instanceof DependencyPreparationStarted) {
            mapper.updatePhase(instanceId, InstancePhase.PREPARING_DEPENDENCIES, version, timestamp);
        } else if (event instanceof DependenciesPrepared prepared) {
            mapper.updateDependencies(instanceId, InstancePhase.PREPARING_DEPENDENCIES, prepared.dependencies(), version, timestamp);
        } else if (event instanceof SecurityGroupPreparationStarted) {
            mapper.updatePhase(instanceId, InstancePhase.PREPARING_SECURITY_GROUP, version, timestamp);
        } else if (event instanceof SecurityGroupPrepared prepared) {
            mapper.updateSecurityGroup(instanceId, InstancePhase.PREPARING_SECURITY_GROUP, prepared.osSecurityGroupId(), version, timestamp);
        } else if (event instanceof SchedulingStarted started) {
            mapper.updateScheduling(instanceId, InstancePhase.SCHEDULING, started.reservationId(), version, timestamp);
        } else if (event instanceof NetworkPreparationStarted) {
            mapper.updatePhase(instanceId, InstancePhase.PREPARING_NETWORK, version, timestamp);
        } else if (event instanceof NetworkPrepared prepared) {
            mapper.updateNetwork(instanceId, InstancePhase.PREPARING_NETWORK, prepared.resources(), version, timestamp);
        } else if (event instanceof ForwardingPreparationStarted) {
            mapper.updatePhase(instanceId, InstancePhase.PREPARING_FORWARDING, version, timestamp);
        } else if (event instanceof InstanceForwardingEvent
                || event instanceof InstanceQosUpdateRequested || event instanceof InstanceQosUpdateFailed
                || event instanceof InstanceQosExpiryRequested || event instanceof InstanceQosExpiryFailed) {
            mapper.updateVersion(instanceId, version, timestamp);
        } else if (event instanceof InstanceQosUpdated updated) {
            mapper.updateQos(instanceId, updated.resources(), version, timestamp);
        } else if (event instanceof InstanceQosExpired expired) {
            mapper.updateQos(instanceId, expired.resources(), version, timestamp);
        } else if (event instanceof InstanceServerOperationRequested requested) {
            mapper.updateServerOperation(instanceId, requested.operation().type().instancePhase(), requested.operation(),
                    null, null, null, version, timestamp);
        } else if (event instanceof InstanceServerOperationSubmitted submitted) {
            mapper.updateServerOperation(instanceId, submitted.operation().type().instancePhase(), submitted.operation(),
                    null, null, null, version, timestamp);
        } else if (event instanceof InstanceServerOperationCompleted completed) {
            mapper.updateServerOperation(instanceId, InstancePhase.ACTIVE, completed.operation(),
                    "ACTIVE", completed.osImageId(), null, version, timestamp);
        } else if (event instanceof InstanceServerOperationFailed failed) {
            mapper.updateServerOperation(instanceId, InstancePhase.FAILED, failed.operation(),
                    failed.novaStatus() == null ? null : failed.novaStatus().name(), null,
                    failed.failureCode(), version, timestamp);
        } else if (event instanceof HypervisorReservationStarted started) {
            mapper.updateHypervisor(instanceId, InstancePhase.RESERVING_RESOURCES, started.hypervisorId(),
                    started.hostname(), version, timestamp);
        } else if (event instanceof HypervisorSelected selected) {
            mapper.updateHypervisor(instanceId, InstancePhase.RESERVING_RESOURCES, selected.hypervisorId(),
                    selected.hostname(), version, timestamp);
        } else if (event instanceof NovaCreationStarted) {
            mapper.updatePhase(instanceId, InstancePhase.PROVISIONING, version, timestamp);
        } else if (event instanceof NovaServerCreated created) {
            mapper.updateNovaServer(instanceId, InstancePhase.PROVISIONING, created.osServerId(),
                    created.status().name(), version, timestamp);
        } else if (event instanceof SshConnectivityVerified || event instanceof ReservationConfirmedForInstance) {
            mapper.updatePhase(instanceId, InstancePhase.CONFIRMING, version, timestamp);
        } else if (event instanceof InstanceDeletionStarted) {
            mapper.updatePhase(instanceId, InstancePhase.DELETING_NOVA, version, timestamp);
        } else if (event instanceof NovaDeletionAccepted accepted) {
            mapper.updateNovaDeletion(instanceId, InstancePhase.DELETING_NOVA, accepted.osServerId(), version, timestamp);
        } else if (event instanceof NovaServerDeleted) {
            mapper.updatePhase(instanceId, InstancePhase.DELETING_FORWARDINGS, version, timestamp);
        } else if (event instanceof InstanceForwardingsDeleted) {
            mapper.updatePhase(instanceId, InstancePhase.DELETING_NETWORK, version, timestamp);
        } else if (event instanceof InstanceNetworkDeleted) {
            mapper.updatePhase(instanceId, InstancePhase.DELETING_SECURITY_GROUP, version, timestamp);
        } else if (event instanceof InstanceSecurityGroupDeleted) {
            mapper.updatePhase(instanceId, InstancePhase.CANCELLING_RESERVATION, version, timestamp);
        } else if (event instanceof InstanceReservationCancelled) {
            mapper.updatePhase(instanceId, InstancePhase.RELEASING_ALLOCATION, version, timestamp);
        } else if (event instanceof InstanceDeleted) {
            mapper.updatePhase(instanceId, InstancePhase.DELETED, version, timestamp);
        } else if (event instanceof InstanceActivated) {
            mapper.updateFailure(instanceId, InstancePhase.ACTIVE, null, null, version, timestamp);
        } else if (event instanceof InstanceProvisioningFailed failed) {
            mapper.updateFailure(instanceId, InstancePhase.FAILED, failed.failureCode(),
                    failed.failureMessage(), version, timestamp);
        } else if (event instanceof InstanceDeletionFailed failed) {
            mapper.updateFailure(instanceId, InstancePhase.FAILED, failed.failureCode(),
                    failed.failureMessage(), version, timestamp);
        }
    }

    static String entityId(String persistenceId) {
        int separator = persistenceId.indexOf('|');
        if (separator < 0 || separator == persistenceId.length() - 1) {
            throw new IllegalArgumentException("unexpected persistence id: " + persistenceId);
        }
        return persistenceId.substring(separator + 1);
    }
}
