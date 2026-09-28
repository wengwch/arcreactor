package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record InstanceActivated(String instanceId, String novaServerId, String hypervisorId,
        Instant activatedAt) implements InstanceWorkflowEvent {
}
