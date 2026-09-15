package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.Objects;

public record EnterMaintenance(ActorRef<ActionReply> replyTo) implements HypervisorCommand {
    public EnterMaintenance { Objects.requireNonNull(replyTo); }
}
