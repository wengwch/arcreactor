package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record NovaCreationUnknown(String instanceId, String operationId, String reason,
        Instant occurredAt) implements InstanceWorkflowEvent {
}
