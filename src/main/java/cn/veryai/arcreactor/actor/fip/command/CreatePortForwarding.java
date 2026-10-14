package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record CreatePortForwarding(String claimId, ForwardingSpec spec, ActorRef<ForwardingReply> replyTo)
    implements FloatingIpCommand {
  public CreatePortForwarding {
    if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
    Objects.requireNonNull(spec); Objects.requireNonNull(replyTo);
  }
}
