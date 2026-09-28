package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.openstack.NovaClient;
import java.time.Instant;

public record NovaStatusChanged(String instanceId, String serverId, NovaClient.NovaServer.Status status,
        Instant changedAt) implements InstanceWorkflowEvent {
}
