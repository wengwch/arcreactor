package cn.veryai.arcreactor.actor.instance.projection;

import cn.veryai.arcreactor.actor.instance.event.InstanceDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceNetworkDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosExpired;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosExpiryFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosExpiryRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdated;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.actor.instance.projection.mybatis.InstanceQosProjectionMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** History, current selection and projection offset share one JDBC transaction. */
public final class InstanceQosProjectionHandler
        extends JdbcHandler<EventEnvelope<InstanceWorkflowEvent>, JdbcProjectionSession> {
    @Override
    public void process(JdbcProjectionSession session, EventEnvelope<InstanceWorkflowEvent> envelope) {
        InstanceQosProjectionMapper mapper = session.mapper(InstanceQosProjectionMapper.class);
        String instanceId = InstanceProjectionHandler.entityId(envelope.persistenceId());
        long version = envelope.sequenceNr();
        Timestamp timestamp = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));
        InstanceWorkflowEvent event = envelope.event();
        if (event instanceof InstanceNetworkDeleted || event instanceof InstanceDeleted) {
            mapper.cancelOutstanding(instanceId, version, timestamp);
            mapper.deleteCurrent(instanceId, null, version);
            return;
        }
        InstanceQosUpdate update;
        if (event instanceof InstanceQosUpdateRequested requested) update = requested.update();
        else if (event instanceof InstanceQosUpdated updated) update = updated.update();
        else if (event instanceof InstanceQosUpdateFailed failed) update = failed.update();
        else if (event instanceof InstanceQosExpiryRequested requested) update = requested.update();
        else if (event instanceof InstanceQosExpiryFailed failed) update = failed.update();
        else if (event instanceof InstanceQosExpired expired) update = expired.update();
        else return;

        Long projectedVersion = mapper.findRecordVersion(instanceId, update.operationId());
        if (projectedVersion != null && projectedVersion >= version) return;
        if (event instanceof InstanceQosUpdateRequested) {
            if (projectedVersion != null) {
                throw new IllegalStateException("QoS operation ID was reused: " + update.operationId());
            }
            Timestamp expiresAt = Timestamp.from(update.expiresAt());
            mapper.insertRequested(instanceId, update, expiresAt, version, timestamp);
            return;
        }
        if (projectedVersion == null) {
            throw new IllegalStateException("QoS request record is missing; replay the projection from its request: "
                    + instanceId + "/" + update.operationId());
        }
        if (event instanceof InstanceQosUpdated updated) {
            Timestamp switchedAt = Timestamp.from(updated.switchedAt());
            if (updated.replacedQosId() != null
                    && mapper.cancelReplaced(instanceId, updated.replacedQosId(), switchedAt, version, timestamp) == 0) {
                throw new IllegalStateException("Replaced QoS record is missing or already finished: "
                        + instanceId + "/" + updated.replacedQosId());
            }
            mapper.activateRecord(instanceId, update, switchedAt, updated.replacedQosId(), version, timestamp);
            mapper.upsertCurrent(instanceId, update, Timestamp.from(update.expiresAt()), switchedAt,
                    updated.replacedQosId(), version, timestamp);
            return;
        }
        mapper.updateRecord(instanceId, update, version, timestamp);
        if (event instanceof InstanceQosExpired) {
            mapper.deleteCurrent(instanceId, update.operationId(), version);
        } else if ((event instanceof InstanceQosExpiryRequested || event instanceof InstanceQosExpiryFailed)
                && mapper.updateCurrent(instanceId, update, version, timestamp) == 0) {
            throw new IllegalStateException("Current QoS record is missing or differs from the event: "
                    + instanceId + "/" + update.operationId());
        }
    }
}
