package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.reply.ReleaseReply;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.Objects;

public record ReleaseResources(String instanceId, ActorRef<ReleaseReply> replyTo) implements HypervisorCommand {
    public ReleaseResources { Objects.requireNonNull(instanceId); Objects.requireNonNull(replyTo); }
}
