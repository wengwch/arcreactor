package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;
import java.util.List;

public record HypervisorReservationStarted(String instanceId, String hypervisorId, String reservationId,
        List<String> remainingHypervisorIds, Instant startedAt) implements InstanceWorkflowEvent {
    public HypervisorReservationStarted {
        remainingHypervisorIds = List.copyOf(remainingHypervisorIds);
    }
}
