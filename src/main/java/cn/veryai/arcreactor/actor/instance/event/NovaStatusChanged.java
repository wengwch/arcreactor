package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.time.Instant;

public record NovaStatusChanged(String instanceId, String serverId, OpenStackClient.NovaServer.Status status,
        Instant changedAt) implements InstanceWorkflowEvent {
}
