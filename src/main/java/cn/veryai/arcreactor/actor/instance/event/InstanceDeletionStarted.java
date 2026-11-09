package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

/** Deletion is authorized by an external DeleteInstance command. */
public record InstanceDeletionStarted(String instanceId, Instant startedAt) implements InstanceWorkflowEvent {
}
