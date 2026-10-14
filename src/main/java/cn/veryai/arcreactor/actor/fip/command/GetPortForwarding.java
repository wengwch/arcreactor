package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record GetPortForwarding(String claimId, ActorRef<ForwardingReply> replyTo) implements FloatingIpCommand {
  public GetPortForwarding {
    if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
    Objects.requireNonNull(replyTo);
  }
}
