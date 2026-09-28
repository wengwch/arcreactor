package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record NovaEnteredError(String instanceId, String serverId, String reason,
        Instant enteredAt) implements InstanceWorkflowEvent {
}
