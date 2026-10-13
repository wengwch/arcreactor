package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.time.Instant;

public record NovaServerCreated(String instanceId, String operationId, String serverId,
        OpenStackClient.NovaServer.Status status, Instant createdAt) implements InstanceWorkflowEvent {
    public NovaServerCreated {
        if (status == null) status = OpenStackClient.NovaServer.Status.BUILD;
    }

    public NovaServerCreated(String instanceId, String operationId, String serverId, Instant createdAt) {
        this(instanceId, operationId, serverId, OpenStackClient.NovaServer.Status.BUILD, createdAt);
    }
}
