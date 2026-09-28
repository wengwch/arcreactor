package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record SchedulingStarted(String instanceId, String requestId, int attempt,
        Instant startedAt) implements InstanceWorkflowEvent {
}
