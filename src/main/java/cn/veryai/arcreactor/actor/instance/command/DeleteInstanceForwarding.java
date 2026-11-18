package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record DeleteInstanceForwarding(ForwardingScope scope, String forwardingId, long generation,
    ActorRef<InstanceMutationReply> replyTo) implements WorkflowCommand {
  public DeleteInstanceForwarding {
    Objects.requireNonNull(scope, "scope");
    if (scope == ForwardingScope.USER) {
      if (forwardingId == null || forwardingId.isBlank()) throw new IllegalArgumentException("forwardingId is required");
      Objects.requireNonNull(replyTo, "replyTo");
    } else if (forwardingId != null || replyTo != null) {
      throw new IllegalArgumentException("Instance cleanup takes only the workflow generation");
    }
  }

  public static DeleteInstanceForwarding user(String forwardingId, ActorRef<InstanceMutationReply> replyTo) {
    return new DeleteInstanceForwarding(ForwardingScope.USER, forwardingId, 0, replyTo);
  }

  public static DeleteInstanceForwarding all(long generation) {
    return new DeleteInstanceForwarding(ForwardingScope.INSTANCE, null, generation, null);
  }
}
