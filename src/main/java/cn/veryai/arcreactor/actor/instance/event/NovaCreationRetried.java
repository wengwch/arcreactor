package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record NovaCreationRetried(String instanceId, String operationId,
        Instant retriedAt) implements InstanceWorkflowEvent {
}
