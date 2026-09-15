package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.model.ResourceCapacity;
import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.Objects;

public record UpdateCapacity(ResourceCapacity capacity, ActorRef<ActionReply> replyTo) implements HypervisorCommand {
    public UpdateCapacity { Objects.requireNonNull(capacity); Objects.requireNonNull(replyTo); }
}
