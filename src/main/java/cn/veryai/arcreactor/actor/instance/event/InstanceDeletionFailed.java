package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record InstanceDeletionFailed(String instanceId, String failureCode,
        String failureMessage, Instant failedAt) implements InstanceWorkflowEvent {
}
