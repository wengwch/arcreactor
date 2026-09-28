package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.openstack.NovaClient;
import java.time.Instant;

public record NovaServerCreated(String instanceId, String operationId, String serverId,
        NovaClient.NovaServer.Status status, Instant createdAt) implements InstanceWorkflowEvent {
    public NovaServerCreated {
        if (status == null) status = NovaClient.NovaServer.Status.BUILD;
    }

    public NovaServerCreated(String instanceId, String operationId, String serverId, Instant createdAt) {
        this(instanceId, operationId, serverId, NovaClient.NovaServer.Status.BUILD, createdAt);
    }
}
