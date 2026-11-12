package cn.veryai.arcreactor.actor.hypervisor.projection;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import cn.veryai.arcreactor.actor.hypervisor.event.CapacityUpdated;
import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorDrainStarted;
import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorEnabled;
import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorEvent;
import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorMaintenanceEntered;
import cn.veryai.arcreactor.actor.hypervisor.event.ReservationCancelled;
import cn.veryai.arcreactor.actor.hypervisor.event.ReservationConfirmed;
import cn.veryai.arcreactor.actor.hypervisor.event.ReservationOutcomesCleaned;
import cn.veryai.arcreactor.actor.hypervisor.event.ReservationRejected;
import cn.veryai.arcreactor.actor.hypervisor.event.ResourcesReleased;
import cn.veryai.arcreactor.actor.hypervisor.event.ResourcesReserved;
import cn.veryai.arcreactor.actor.hypervisor.projection.mybatis.HypervisorProjectionMapper;
import cn.veryai.arcreactor.enums.HypervisorStatus;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** Projects host resource counters and confirmed instance counts in the offset transaction. */
public final class HypervisorProjectionHandler
        extends JdbcHandler<EventEnvelope<HypervisorEvent>, JdbcProjectionSession> {

    @Override
    public void process(JdbcProjectionSession session, EventEnvelope<HypervisorEvent> envelope) {
        HypervisorEvent event = envelope.event();
        HypervisorProjectionMapper mapper = session.mapper(HypervisorProjectionMapper.class);
        String hypervisorId = entityId(envelope.persistenceId());
        long version = envelope.sequenceNr();
        Timestamp timestamp = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));
        int written;

        if (event instanceof CapacityUpdated updated) {
            written = mapper.updateCapacity(hypervisorId, updated.capacity(), updated.baselineUsage(), version, timestamp);
        } else if (event instanceof ResourcesReserved reserved) {
            written = mapper.applyResourceDelta(hypervisorId, reserved.reservation().resources(), 1, 0, version, timestamp);
        } else if (event instanceof ReservationConfirmed confirmed) {
            written = mapper.applyResourceDelta(hypervisorId, confirmed.reservation().resources(), -1, 1, version, timestamp);
        } else if (event instanceof ReservationCancelled cancelled) {
            written = mapper.applyResourceDelta(hypervisorId, cancelled.reservation().resources(), -1, 0, version, timestamp);
        } else if (event instanceof ResourcesReleased released) {
            written = mapper.applyResourceDelta(hypervisorId, released.allocation().resources(), 0, -1, version, timestamp);
        } else if (event instanceof HypervisorEnabled) {
            written = mapper.updateStatus(hypervisorId, HypervisorStatus.ACTIVE, null, version, timestamp);
        } else if (event instanceof HypervisorDrainStarted started) {
            written = mapper.updateStatus(hypervisorId, HypervisorStatus.DRAINING,
                    started.expectedMaintenanceTime(), version, timestamp);
        } else if (event instanceof HypervisorMaintenanceEntered) {
            written = mapper.updateStatus(hypervisorId, HypervisorStatus.MAINTENANCE, null, version, timestamp);
        } else if (event instanceof ReservationRejected || event instanceof ReservationOutcomesCleaned) {
            written = mapper.advanceVersion(hypervisorId, version, timestamp);
        } else {
            throw new IllegalArgumentException("unsupported hypervisor event: " + event.getClass().getName());
        }
        if (written == 0) {
            Long projectedVersion = mapper.findResourceVersion(hypervisorId);
            if (projectedVersion == null || projectedVersion < version) {
                // Host registration must be visible before its actor events can advance the projection offset.
                throw new IllegalStateException("hypervisor host is not available for projection: " + hypervisorId);
            }
        }
    }

    static String entityId(String persistenceId) {
        String prefix = HypervisorActor.ENTITY_TYPE + "|";
        if (persistenceId == null || !persistenceId.startsWith(prefix)
                || persistenceId.substring(prefix.length()).isBlank()) {
            throw new IllegalArgumentException("unexpected hypervisor persistence id: " + persistenceId);
        }
        return persistenceId.substring(prefix.length());
    }
}
