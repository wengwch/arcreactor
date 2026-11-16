package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record DeleteUserForwarding(String forwardingId, ActorRef<InstanceMutationReply> replyTo)
    implements WorkflowCommand {
  public DeleteUserForwarding {
    if (forwardingId == null || forwardingId.isBlank()) throw new IllegalArgumentException("forwardingId is required");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
