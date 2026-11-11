package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record SchedulingStarted(String instanceId, String requestId,
        Instant startedAt) implements InstanceWorkflowEvent {
}
