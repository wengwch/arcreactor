package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record HypervisorSelected(String instanceId, String hypervisorId, String reservationId,
        Instant selectedAt) implements InstanceWorkflowEvent {
}
