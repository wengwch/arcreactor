package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.Objects;

public record EnableHypervisor(ActorRef<ActionReply> replyTo) implements HypervisorCommand {
    public EnableHypervisor { Objects.requireNonNull(replyTo); }
}
