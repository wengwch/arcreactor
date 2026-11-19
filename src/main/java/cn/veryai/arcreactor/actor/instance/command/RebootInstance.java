package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** Requests a hard reboot of the existing Nova server. */
public record RebootInstance(ActorRef<InstanceMutationReply> replyTo) implements WorkflowCommand {
  public RebootInstance {
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
