package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.util.Objects;

public record NovaStatusSucceeded(String serverId, long generation, int attempt,
        OpenStackClient.NovaServer.Status status, String fault) implements WorkflowCommand {
    public NovaStatusSucceeded { Objects.requireNonNull(status, "status"); }

    public NovaStatusSucceeded(String serverId, long generation, OpenStackClient.NovaServer.Status status,
            String fault) {
        this(serverId, generation, 1, status, fault);
    }
}
