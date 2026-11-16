package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record CreateUserForwarding(int internalPort, ActorRef<InstanceMutationReply> replyTo)
    implements WorkflowCommand {
  public CreateUserForwarding {
    if (internalPort < 1 || internalPort > 65535) throw new IllegalArgumentException("invalid internal port");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
