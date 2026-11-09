package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.model.ResourceCapacity;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceUsage;
import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record UpdateCapacity(ResourceCapacity capacity, ResourceUsage baselineUsage,
        ActorRef<ActionReply> replyTo) implements HypervisorCommand {
    public UpdateCapacity {
        Objects.requireNonNull(capacity);
        Objects.requireNonNull(baselineUsage);
        Objects.requireNonNull(replyTo);
    }
}
