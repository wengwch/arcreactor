package cn.veryai.arcreactor.actor.instance.projection;

import cn.veryai.arcreactor.actor.instance.event.CompensationStarted;
import cn.veryai.arcreactor.actor.instance.event.HypervisorSelected;
import cn.veryai.arcreactor.actor.instance.event.InstanceActivated;
import cn.veryai.arcreactor.actor.instance.event.InstanceCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceProvisioningFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationRetried;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationStarted;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationUnknown;
import cn.veryai.arcreactor.actor.instance.event.NovaEnteredError;
import cn.veryai.arcreactor.actor.instance.event.NovaReachedActive;
import cn.veryai.arcreactor.actor.instance.event.NovaServerCreated;
import cn.veryai.arcreactor.actor.instance.event.NovaStatusChanged;
import cn.veryai.arcreactor.actor.instance.event.ProvisioningDeadlineReached;
import cn.veryai.arcreactor.actor.instance.event.ReservationConfirmedForInstance;
import cn.veryai.arcreactor.actor.instance.event.ResourcesReservedForInstance;
import cn.veryai.arcreactor.actor.instance.event.SchedulingStarted;
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
            mapper.upsertCreated(instanceId, created.requestId(), "NEW", version,
                    Timestamp.from(created.requestedAt()), timestamp);
        } else if (event instanceof SchedulingStarted) {
            mapper.updatePhase(instanceId, "SCHEDULING", version, timestamp);
        } else if (event instanceof HypervisorSelected selected) {
            mapper.updateHypervisor(instanceId, "RESERVING", selected.hypervisorId(),
                    selected.reservationId(), version, timestamp);
        } else if (event instanceof ResourcesReservedForInstance) {
            mapper.updatePhase(instanceId, "RESERVED", version, timestamp);
        } else if (event instanceof NovaCreationStarted started) {
            mapper.updateNovaOperation(instanceId, "NOVA_CREATING", started.operationId(), version, timestamp);
        } else if (event instanceof NovaCreationRetried) {
            mapper.updatePhase(instanceId, "NOVA_CREATING", version, timestamp);
        } else if (event instanceof NovaCreationUnknown || event instanceof ProvisioningDeadlineReached) {
            mapper.updatePhase(instanceId, "RECONCILING", version, timestamp);
        } else if (event instanceof NovaServerCreated created) {
            mapper.updateNovaServer(instanceId, "BUILDING", created.serverId(),
                    created.status().name(), version, timestamp);
        } else if (event instanceof NovaStatusChanged changed) {
            mapper.updateNovaServer(instanceId, "BUILDING", changed.serverId(),
                    changed.status().name(), version, timestamp);
        } else if (event instanceof NovaReachedActive active) {
            mapper.updateNovaServer(instanceId, "CONFIRMING", active.serverId(),
                    "ACTIVE", version, timestamp);
        } else if (event instanceof NovaEnteredError error) {
            mapper.updateNovaServer(instanceId, "BUILDING", error.serverId(),
                    "ERROR", version, timestamp);
        } else if (event instanceof ReservationConfirmedForInstance) {
            mapper.updatePhase(instanceId, "CONFIRMING", version, timestamp);
        } else if (event instanceof CompensationStarted compensation) {
            mapper.updateFailure(instanceId, "COMPENSATING", compensation.failureCode(),
                    compensation.failureMessage(), version, timestamp);
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
