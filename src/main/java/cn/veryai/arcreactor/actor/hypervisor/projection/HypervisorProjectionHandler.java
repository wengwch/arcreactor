package cn.veryai.arcreactor.actor.hypervisor.projection;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import cn.veryai.arcreactor.actor.hypervisor.event.CapacityUpdated;
import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorDrainStarted;
import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorEnabled;
import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorEvent;
import cn.veryai.arcreactor.actor.hypervisor.event.HypervisorMaintenanceEntered;
import cn.veryai.arcreactor.actor.hypervisor.event.ReservationCancelled;
import cn.veryai.arcreactor.actor.hypervisor.event.ReservationConfirmed;
import cn.veryai.arcreactor.actor.hypervisor.event.ReservationExpired;
import cn.veryai.arcreactor.actor.hypervisor.event.ResourcesReleased;
import cn.veryai.arcreactor.actor.hypervisor.event.ResourcesReserved;
import cn.veryai.arcreactor.actor.hypervisor.model.Allocation;
import cn.veryai.arcreactor.actor.hypervisor.projection.mybatis.HypervisorProjectionMapper;
import cn.veryai.arcreactor.enums.HypervisorStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.jdbc.javadsl.JdbcHandler;

/** Projects capacity, reservations, allocations and host status in the offset transaction. */
public final class HypervisorProjectionHandler
        extends JdbcHandler<EventEnvelope<HypervisorEvent>, JdbcProjectionSession> {

    @Override
    public void process(JdbcProjectionSession session, EventEnvelope<HypervisorEvent> envelope) {
        HypervisorEvent event = envelope.event();
        HypervisorProjectionMapper mapper = session.mapper(HypervisorProjectionMapper.class);
        String hypervisorId = entityId(envelope.persistenceId());
        long version = envelope.sequenceNr();
        Timestamp timestamp = Timestamp.from(Instant.ofEpochMilli(envelope.timestamp()));

        if (event instanceof CapacityUpdated updated) {
            mapper.upsertCapacity(hypervisorId, updated.capacity(), version, timestamp);
            mapper.refreshHostRunningVms(hypervisorId);
        } else if (event instanceof ResourcesReserved reserved) {
            mapper.applyResourceDelta(hypervisorId, reserved.reservation().resources(), 1, 0, version, timestamp);
        } else if (event instanceof ReservationConfirmed confirmed) {
            var reservation = confirmed.reservation();
            if (mapper.applyResourceDelta(hypervisorId, reservation.resources(), -1, 1, version, timestamp) == 1) {
                var allocation = new Allocation(reservation.instanceId(), reservation.reservationId(),
                        reservation.requestId(), reservation.resources(), confirmed.allocatedAt());
                mapper.upsertAllocation(hypervisorId, allocation, "ALLOCATED", version, timestamp);
                mapper.refreshHostRunningVms(hypervisorId);
            }
        } else if (event instanceof ReservationCancelled cancelled) {
            mapper.applyResourceDelta(hypervisorId, cancelled.reservation().resources(), -1, 0, version, timestamp);
        } else if (event instanceof ReservationExpired expired) {
            mapper.applyResourceDelta(hypervisorId, expired.reservation().resources(), -1, 0, version, timestamp);
        } else if (event instanceof ResourcesReleased released) {
            var allocation = released.allocation();
            if (mapper.applyResourceDelta(hypervisorId, allocation.resources(), 0, -1, version, timestamp) == 1) {
                mapper.upsertAllocation(hypervisorId, allocation, "RELEASED", version, timestamp);
                mapper.refreshHostRunningVms(hypervisorId);
            }
        } else if (event instanceof HypervisorEnabled) {
            updateStatus(mapper, hypervisorId, HypervisorStatus.ACTIVE, null, version, timestamp);
        } else if (event instanceof HypervisorDrainStarted started) {
            updateStatus(mapper, hypervisorId, HypervisorStatus.DRAINING,
                    started.expectedMaintenanceTime(), version, timestamp);
        } else if (event instanceof HypervisorMaintenanceEntered) {
            updateStatus(mapper, hypervisorId, HypervisorStatus.MAINTENANCE, null, version, timestamp);
        }
        // ReservationOutcomesCleaned only changes the actor's deduplication history.
    }

    private static void updateStatus(HypervisorProjectionMapper mapper, String hypervisorId,
            HypervisorStatus status, LocalDateTime expectedMaintenanceTime, long version, Timestamp timestamp) {
        // Share the resource version guard with the host row so old events cannot restore an old status or plan.
        if (mapper.updateStatus(hypervisorId, status, version, timestamp) == 1) {
            mapper.updateHostStatus(hypervisorId, status, expectedMaintenanceTime);
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
