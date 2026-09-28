package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record NovaReachedActive(String instanceId, String serverId,
        Instant reachedAt) implements InstanceWorkflowEvent {
}
