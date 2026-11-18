package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record CreateInstanceForwarding(int internalPort, boolean instanceInitialization,
    long generation, ActorRef<InstanceMutationReply> replyTo) implements WorkflowCommand {
  public CreateInstanceForwarding {
    if (internalPort < 1 || internalPort > 65535) throw new IllegalArgumentException("invalid internal port");
    if (!instanceInitialization) Objects.requireNonNull(replyTo, "replyTo");
  }
}
