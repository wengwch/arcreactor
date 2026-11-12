package cn.veryai.arcreactor.actor.instance.projection;

import cn.veryai.arcreactor.actor.instance.event.DependenciesPrepared;
import cn.veryai.arcreactor.actor.instance.event.DependencyPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCandidateSelected;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPrepared;
import cn.veryai.arcreactor.actor.instance.event.HypervisorReservationStarted;
import cn.veryai.arcreactor.actor.instance.event.HypervisorSelected;
import cn.veryai.arcreactor.actor.instance.event.InstanceActivated;
import cn.veryai.arcreactor.actor.instance.event.InstanceCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceDeletionStarted;
import cn.veryai.arcreactor.actor.instance.event.InstanceDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceForwardingsDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceNetworkDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceProvisioningFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceReservationCancelled;
import cn.veryai.arcreactor.actor.instance.event.InstanceSecurityGroupDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationStarted;
import cn.veryai.arcreactor.actor.instance.event.NovaDeletionAccepted;
import cn.veryai.arcreactor.actor.instance.event.NovaReachedActive;
import cn.veryai.arcreactor.actor.instance.event.NovaServerCreated;
import cn.veryai.arcreactor.actor.instance.event.NovaServerDeleted;
import cn.veryai.arcreactor.actor.instance.event.ParentPortPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ParentPortPrepared;
import cn.veryai.arcreactor.actor.instance.event.ReservationConfirmedForInstance;
import cn.veryai.arcreactor.actor.instance.event.SchedulingStarted;
import cn.veryai.arcreactor.actor.instance.event.SecurityGroupPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.SecurityGroupPrepared;
import cn.veryai.arcreactor.actor.instance.event.SubPortPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.SubPortPrepared;
import cn.veryai.arcreactor.actor.instance.event.TrunkPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.TrunkPrepared;
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
            mapper.upsertCreated(instanceId, created.requestId(), created.spec(), "SCHEDULING", version,
                    Timestamp.from(created.requestedAt()), timestamp);
        } else if (event instanceof DependencyPreparationStarted) {
            mapper.updatePhase(instanceId, "PREPARING_DEPENDENCIES", version, timestamp);
        } else if (event instanceof DependenciesPrepared prepared) {
            mapper.updateDependencies(instanceId, "PREPARING_DEPENDENCIES", prepared.dependencies(), version, timestamp);
        } else if (event instanceof SecurityGroupPreparationStarted) {
            mapper.updatePhase(instanceId, "PREPARING_SECURITY_GROUP", version, timestamp);
        } else if (event instanceof SecurityGroupPrepared prepared) {
            mapper.updateSecurityGroup(instanceId, "PREPARING_SECURITY_GROUP", prepared.osSecurityGroupId(), version, timestamp);
        } else if (event instanceof SchedulingStarted) {
            mapper.updatePhase(instanceId, "SCHEDULING", version, timestamp);
        } else if (event instanceof ParentPortPreparationStarted) {
            mapper.updatePhase(instanceId, "PREPARING_PARENT_PORT", version, timestamp);
        } else if (event instanceof ParentPortPrepared prepared) {
            mapper.updateNetwork(instanceId, "PREPARING_PARENT_PORT", prepared.resources(), version, timestamp);
        } else if (event instanceof SubPortPreparationStarted) {
            mapper.updatePhase(instanceId, "PREPARING_SUB_PORT", version, timestamp);
        } else if (event instanceof SubPortPrepared prepared) {
            mapper.updateNetwork(instanceId, "PREPARING_SUB_PORT", prepared.resources(), version, timestamp);
        } else if (event instanceof TrunkPreparationStarted) {
            mapper.updatePhase(instanceId, "PREPARING_TRUNK", version, timestamp);
        } else if (event instanceof TrunkPrepared prepared) {
            mapper.updateNetwork(instanceId, "PREPARING_TRUNK", prepared.resources(), version, timestamp);
        } else if (event instanceof ForwardingPreparationStarted
                || event instanceof ForwardingCandidateSelected || event instanceof ForwardingPrepared) {
            mapper.updatePhase(instanceId, "PREPARING_FORWARDING", version, timestamp);
        } else if (event instanceof HypervisorReservationStarted started) {
            mapper.updateHypervisor(instanceId, "RESERVING_RESOURCES", started.hypervisorId(),
                    started.reservationId(), version, timestamp);
        } else if (event instanceof HypervisorSelected selected) {
            mapper.updateHypervisor(instanceId, "RESERVING_RESOURCES", selected.hypervisorId(),
                    selected.reservationId(), version, timestamp);
        } else if (event instanceof NovaCreationStarted started) {
            mapper.updateNovaOperation(instanceId, "PROVISIONING", started.operationId(), version, timestamp);
        } else if (event instanceof NovaServerCreated created) {
            mapper.updateNovaServer(instanceId, "PROVISIONING", created.osServerId(),
                    created.status().name(), version, timestamp);
        } else if (event instanceof NovaReachedActive active) {
            mapper.updateNovaServer(instanceId, "CONFIRMING", active.osServerId(),
                    "ACTIVE", version, timestamp);
        } else if (event instanceof ReservationConfirmedForInstance) {
            mapper.updatePhase(instanceId, "CONFIRMING", version, timestamp);
        } else if (event instanceof InstanceDeletionStarted) {
            mapper.updatePhase(instanceId, "DELETING_NOVA", version, timestamp);
        } else if (event instanceof NovaDeletionAccepted accepted) {
            mapper.updateNovaDeletion(instanceId, accepted.osServerId(), version, timestamp);
        } else if (event instanceof NovaServerDeleted) {
            mapper.updatePhase(instanceId, "DELETING_FORWARDINGS", version, timestamp);
        } else if (event instanceof InstanceForwardingsDeleted) {
            mapper.updatePhase(instanceId, "DELETING_NETWORK", version, timestamp);
        } else if (event instanceof InstanceNetworkDeleted) {
            mapper.updatePhase(instanceId, "DELETING_SECURITY_GROUP", version, timestamp);
        } else if (event instanceof InstanceSecurityGroupDeleted) {
            mapper.updatePhase(instanceId, "CANCELLING_RESERVATION", version, timestamp);
        } else if (event instanceof InstanceReservationCancelled) {
            mapper.updatePhase(instanceId, "RELEASING_ALLOCATION", version, timestamp);
        } else if (event instanceof InstanceDeleted) {
            mapper.updatePhase(instanceId, "DELETED", version, timestamp);
        } else if (event instanceof InstanceActivated) {
            mapper.updateFailure(instanceId, "ACTIVE", null, null, version, timestamp);
        } else if (event instanceof InstanceProvisioningFailed failed) {
            mapper.updateFailure(instanceId, "FAILED", failed.failureCode(),
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
