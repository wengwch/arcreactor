package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import org.apache.pekko.actor.typed.ActorRef;

import java.time.LocalDateTime;
import java.util.Objects;

public record DrainHypervisor(LocalDateTime expectedMaintenanceTime, ActorRef<ActionReply> replyTo)
        implements HypervisorCommand {
    public DrainHypervisor { Objects.requireNonNull(replyTo); }

    public DrainHypervisor(ActorRef<ActionReply> replyTo) {
        this(null, replyTo);
    }
}
