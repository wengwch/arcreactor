package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record SchedulingStarted(String instanceId, String requestId, String reservationId,
        Instant startedAt) implements InstanceWorkflowEvent {
}
