package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.reply.NetworkReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record GetNetwork(String regionId, ActorRef<NetworkReply> replyTo) implements UserCommand {
  public GetNetwork {
    if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId is required");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
