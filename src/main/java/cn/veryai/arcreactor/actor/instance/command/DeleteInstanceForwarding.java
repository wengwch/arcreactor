package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record DeleteInstanceForwarding(ForwardingScope scope, String claimId, long generation,
    ActorRef<InstanceMutationReply> replyTo) implements WorkflowCommand {
  public DeleteInstanceForwarding {
    Objects.requireNonNull(scope, "scope");
    if (scope == ForwardingScope.USER) {
      if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
      Objects.requireNonNull(replyTo, "replyTo");
    } else if (claimId != null || replyTo != null) {
      throw new IllegalArgumentException("Instance cleanup takes only the workflow generation");
    }
  }

  public static DeleteInstanceForwarding user(String claimId, ActorRef<InstanceMutationReply> replyTo) {
    return new DeleteInstanceForwarding(ForwardingScope.USER, claimId, 0, replyTo);
  }

  public static DeleteInstanceForwarding all(long generation) {
    return new DeleteInstanceForwarding(ForwardingScope.INSTANCE, null, generation, null);
  }
}
