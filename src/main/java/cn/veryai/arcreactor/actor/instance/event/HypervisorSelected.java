package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record HypervisorSelected(String instanceId, String hypervisorId,
        Instant selectedAt) implements InstanceWorkflowEvent {
}
