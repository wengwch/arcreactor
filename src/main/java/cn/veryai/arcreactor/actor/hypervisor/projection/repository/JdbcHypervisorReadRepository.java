package cn.veryai.arcreactor.actor.hypervisor.projection.repository;

import cn.veryai.arcreactor.actor.hypervisor.event.*;
import cn.veryai.arcreactor.actor.hypervisor.model.HypervisorStatus;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceRequest;
import cn.veryai.arcreactor.mapper.HypervisorProjectionMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

@Component
public final class JdbcHypervisorReadRepository implements HypervisorReadRepository {

    @Override
    public void apply(HypervisorProjectionMapper mapper, String hypervisorId, HypervisorEvent event,
            long sequenceNumber, Instant eventTimestamp) {
        if (event instanceof CapacityUpdated capacityUpdated) {
            applyCapacity(mapper, hypervisorId, capacityUpdated, sequenceNumber, eventTimestamp);
        } else if (event instanceof ResourcesReserved reserved) {
            applyDelta(mapper, hypervisorId, reserved.reservation().resources(), 1, 0,
                    sequenceNumber, eventTimestamp);
        } else if (event instanceof ReservationConfirmed confirmed) {
            ResourceRequest resources = confirmed.reservation().resources();
            if (applyDelta(mapper, hypervisorId, resources, -1, 1, sequenceNumber, eventTimestamp)) {
                upsertAllocation(mapper, hypervisorId, confirmed, sequenceNumber, eventTimestamp);
            }
        } else if (event instanceof ReservationCancelled cancelled) {
            applyDelta(mapper, hypervisorId, cancelled.reservation().resources(), -1, 0,
                    sequenceNumber, eventTimestamp);
        } else if (event instanceof ReservationExpired expired) {
            applyDelta(mapper, hypervisorId, expired.reservation().resources(), -1, 0,
                    sequenceNumber, eventTimestamp);
        } else if (event instanceof ResourcesReleased released) {
            if (applyDelta(mapper, hypervisorId, released.allocation().resources(), 0, -1,
                    sequenceNumber, eventTimestamp)) {
                mapper.markAllocationReleased(released.allocation().instanceId(), sequenceNumber,
                        Timestamp.from(eventTimestamp));
            }
        } else if (event instanceof HypervisorEnabled) {
            applyStatus(mapper, hypervisorId, HypervisorStatus.ACTIVE, sequenceNumber, eventTimestamp);
        } else if (event instanceof HypervisorDrainStarted) {
            applyStatus(mapper, hypervisorId, HypervisorStatus.DRAINING, sequenceNumber, eventTimestamp);
        } else if (event instanceof HypervisorMaintenanceEntered) {
            applyStatus(mapper, hypervisorId, HypervisorStatus.MAINTENANCE, sequenceNumber, eventTimestamp);
        }
    }

    private void applyCapacity(HypervisorProjectionMapper mapper, String hypervisorId, CapacityUpdated event,
            long version, Instant timestamp) {
        mapper.upsertCapacity(
                hypervisorId,
                event.capacity().availabilityZone(),
                event.capacity().totalVcpus(),
                event.capacity().totalMemoryMb(),
                event.capacity().gpus(),
                version,
                Timestamp.from(timestamp));
    }

    private boolean applyDelta(HypervisorProjectionMapper mapper, String hypervisorId, ResourceRequest resources,
            int reservationMultiplier, int allocationMultiplier, long version, Instant timestamp) {
        return mapper.applyResourceDelta(
                hypervisorId,
                resources.vcpus() * reservationMultiplier,
                resources.vcpus() * allocationMultiplier,
                resources.memoryMb() * reservationMultiplier,
                resources.memoryMb() * allocationMultiplier,
                resources.gpuRequest() * reservationMultiplier,
                resources.gpuRequest() * allocationMultiplier,
                version,
                Timestamp.from(timestamp)) == 1;
    }

    private void applyStatus(HypervisorProjectionMapper mapper, String hypervisorId, HypervisorStatus status,
            long version, Instant timestamp) {
        mapper.applyStatus(hypervisorId, status.name(), version, Timestamp.from(timestamp));
    }

    private void upsertAllocation(HypervisorProjectionMapper mapper, String hypervisorId,
            ReservationConfirmed event, long version, Instant timestamp) {
        var reservation = event.reservation();
        mapper.upsertAllocation(
                reservation.instanceId(),
                hypervisorId,
                reservation.reservationId(),
                reservation.resources().vcpus(),
                reservation.resources().memoryMb(),
                reservation.resources().gpuRequest(),
                version,
                Timestamp.from(timestamp));
    }

}
