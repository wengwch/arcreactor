package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.util.Objects;

public record NovaReconcileFound(long generation, OpenStackClient.NovaServer server) implements WorkflowCommand {
    public NovaReconcileFound { Objects.requireNonNull(server, "server"); }
}
