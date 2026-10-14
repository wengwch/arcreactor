package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record DeletePortForwarding(String claimId, ActorRef<ForwardingReply> replyTo) implements FloatingIpCommand {
  public DeletePortForwarding {
    if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
    Objects.requireNonNull(replyTo);
  }
}
