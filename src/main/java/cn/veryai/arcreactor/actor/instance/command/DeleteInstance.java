package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** Explicitly requests deletion of an active or failed instance. */
public record DeleteInstance(ActorRef<InstanceReply> replyTo) implements WorkflowCommand {
    public DeleteInstance {
        Objects.requireNonNull(replyTo, "replyTo");
    }
}
