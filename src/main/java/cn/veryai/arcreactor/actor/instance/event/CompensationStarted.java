package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record CompensationStarted(String instanceId, String failureCode, String failureMessage,
        Instant startedAt) implements InstanceWorkflowEvent {
}
