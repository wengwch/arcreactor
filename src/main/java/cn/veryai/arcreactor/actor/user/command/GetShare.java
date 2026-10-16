package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record GetShare(String regionId, ActorRef<ShareReply> replyTo) implements UserCommand {
  public GetShare {
    if (regionId == null || regionId.isBlank()) {
      throw new IllegalArgumentException("regionId is required");
    }
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
