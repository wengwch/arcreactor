package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record NovaCreationStarted(String instanceId, Instant startedAt) implements InstanceWorkflowEvent {
}
