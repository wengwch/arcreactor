package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record ReservationConfirmedForInstance(String instanceId, String hypervisorId,
        String reservationId, Instant confirmedAt) implements InstanceWorkflowEvent {
}
