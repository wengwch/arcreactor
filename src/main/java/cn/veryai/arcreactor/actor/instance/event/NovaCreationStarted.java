package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record NovaCreationStarted(String instanceId, String operationId, Instant startedAt,
        Instant deadline) implements InstanceWorkflowEvent {
}
