package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record InstanceDeleted(String instanceId, Instant deletedAt) implements InstanceWorkflowEvent {
}
