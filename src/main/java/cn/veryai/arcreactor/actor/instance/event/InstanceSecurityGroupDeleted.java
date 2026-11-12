package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record InstanceSecurityGroupDeleted(String instanceId, Instant completedAt) implements InstanceWorkflowEvent {
}
