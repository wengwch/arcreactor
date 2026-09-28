package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.openstack.NovaClient;
import java.util.Objects;

public record NovaReconcileFound(long generation, NovaClient.NovaServer server) implements WorkflowCommand {
    public NovaReconcileFound { Objects.requireNonNull(server, "server"); }
}
