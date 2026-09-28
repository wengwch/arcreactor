package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import java.time.Instant;

public record ResourcesReservedForInstance(String instanceId, String hypervisorId,
        String reservationId, InstanceSpec spec, Instant reservedAt) implements InstanceWorkflowEvent {
}
