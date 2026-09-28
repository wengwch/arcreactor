package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record InstanceProvisioningFailed(String instanceId, String failureCode,
        String failureMessage, Instant failedAt) implements InstanceWorkflowEvent {
}
