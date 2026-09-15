package cn.veryai.arcreactor.actor.hypervisor.projection.repository;

import cn.veryai.arcreactor.actor.hypervisor.event.*;
import cn.veryai.arcreactor.actor.hypervisor.model.Gpu;
import cn.veryai.arcreactor.actor.hypervisor.model.HypervisorStatus;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceRequest;
import cn.veryai.arcreactor.mapper.HypervisorProjectionMapper;
import com.google.gson.Gson;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Applies an event only when its sequence number is newer than the current row. */
public final class JdbcHypervisorReadRepository implements HypervisorReadRepository {
    private static final Gson GSON = new Gson();

    @Override
    public void apply(HypervisorProjectionMapper mapper, String hypervisorId, HypervisorEvent event,
            long sequenceNumber, Instant eventTimestamp) {
        if (event instanceof CapacityUpdated capacityUpdated) {
            applyCapacity(mapper, hypervisorId, capacityUpdated, sequenceNumber, eventTimestamp);
        } else if (event instanceof ResourcesReserved reserved) {
            if (applyDelta(mapper, hypervisorId, reserved.reservation().resources(), 1, 0,
                    sequenceNumber, eventTimestamp)) {
                updateGpus(mapper, hypervisorId, reserved.reservation().resources().gpuIds(),
                        "RESERVED", reserved.reservation().reservationId(), null, sequenceNumber, eventTimestamp);
            }
        } else if (event instanceof ReservationConfirmed confirmed) {
            ResourceRequest resources = confirmed.reservation().resources();
            if (applyDelta(mapper, hypervisorId, resources, -1, 1, sequenceNumber, eventTimestamp)) {
                upsertAllocation(mapper, hypervisorId, confirmed, sequenceNumber, eventTimestamp);
                updateGpus(mapper, hypervisorId, resources.gpuIds(), "ALLOCATED",
                        confirmed.reservation().reservationId(), confirmed.reservation().instanceId(),
                        sequenceNumber, eventTimestamp);
            }
        } else if (event instanceof ReservationCancelled cancelled) {
            if (applyDelta(mapper, hypervisorId, cancelled.reservation().resources(), -1, 0,
                    sequenceNumber, eventTimestamp)) {
                updateGpus(mapper, hypervisorId, cancelled.reservation().resources().gpuIds(),
                        "AVAILABLE", null, null, sequenceNumber, eventTimestamp);
            }
        } else if (event instanceof ReservationExpired expired) {
            if (applyDelta(mapper, hypervisorId, expired.reservation().resources(), -1, 0,
                    sequenceNumber, eventTimestamp)) {
                updateGpus(mapper, hypervisorId, expired.reservation().resources().gpuIds(),
                        "AVAILABLE", null, null, sequenceNumber, eventTimestamp);
            }
        } else if (event instanceof ResourcesReleased released) {
            if (applyDelta(mapper, hypervisorId, released.allocation().resources(), 0, -1,
                    sequenceNumber, eventTimestamp)) {
                mapper.markAllocationReleased(released.allocation().instanceId(), sequenceNumber,
                        Timestamp.from(eventTimestamp));
                updateGpus(mapper, hypervisorId, released.allocation().resources().gpuIds(),
                        "AVAILABLE", null, null, sequenceNumber, eventTimestamp);
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
        int changed = mapper.upsertCapacity(
                hypervisorId,
                event.capacity().availabilityZone(),
                event.capacity().totalVcpus(),
                event.capacity().totalMemoryMb(),
                event.capacity().gpus().size(),
                version,
                Timestamp.from(timestamp));
        if (changed == 0) return;

        List<String> gpuIds = event.capacity().gpus().stream().map(Gpu::id).toList();
        if (gpuIds.isEmpty()) {
            mapper.deleteAllGpus(hypervisorId);
        } else {
            mapper.deleteGpusExcept(hypervisorId, gpuIds);
        }
        for (Gpu gpu : event.capacity().gpus()) {
            mapper.upsertGpu(hypervisorId, gpu.id(), gpu.model(), json(gpu.traits()),
                    version, Timestamp.from(timestamp));
        }
    }

    private boolean applyDelta(HypervisorProjectionMapper mapper, String hypervisorId, ResourceRequest resources,
            int reservationMultiplier, int allocationMultiplier, long version, Instant timestamp) {
        return mapper.applyResourceDelta(
                hypervisorId,
                resources.vcpus() * reservationMultiplier,
                resources.vcpus() * allocationMultiplier,
                resources.memoryMb() * reservationMultiplier,
                resources.memoryMb() * allocationMultiplier,
                resources.gpuIds().size() * reservationMultiplier,
                resources.gpuIds().size() * allocationMultiplier,
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
                reservation.resources().gpuIds().size(),
                version,
                Timestamp.from(timestamp));
    }

    private void updateGpus(HypervisorProjectionMapper mapper, String hypervisorId, Set<String> gpuIds,
            String status, String reservationId, String instanceId, long version, Instant timestamp) {
        for (String gpuId : gpuIds) {
            mapper.updateGpu(hypervisorId, gpuId, status, reservationId, instanceId,
                    version, Timestamp.from(timestamp));
        }
    }

    private static String json(Set<String> values) {
        return GSON.toJson(values.stream().sorted().toList());
    }
}
