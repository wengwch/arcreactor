package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import java.time.Instant;

public record InstanceCreationRequested(String instanceId, String requestId, InstanceSpec spec,
        Instant requestedAt) implements InstanceWorkflowEvent {
}
