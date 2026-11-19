package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record RebuildInstance(String osImageId, ActorRef<InstanceMutationReply> replyTo)
    implements WorkflowCommand {
  public RebuildInstance {
    if (osImageId == null || osImageId.isBlank()) throw new IllegalArgumentException("osImageId is required");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
