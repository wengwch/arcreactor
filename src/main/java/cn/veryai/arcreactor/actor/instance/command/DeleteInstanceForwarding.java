package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** Deletes one user forwarding; instance cleanup uses its own command. */
public record DeleteInstanceForwarding(String claimId,
    ActorRef<InstanceMutationReply> replyTo) implements WorkflowCommand {
  public DeleteInstanceForwarding {
    if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
