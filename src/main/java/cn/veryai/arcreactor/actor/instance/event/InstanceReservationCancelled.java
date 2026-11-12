package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record InstanceReservationCancelled(String instanceId, Instant completedAt) implements InstanceWorkflowEvent {
}
