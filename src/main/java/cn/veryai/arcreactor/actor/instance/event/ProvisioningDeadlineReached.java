package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record ProvisioningDeadlineReached(String instanceId, String operationId,
        Instant deadline, Instant observedAt) implements InstanceWorkflowEvent {
}
