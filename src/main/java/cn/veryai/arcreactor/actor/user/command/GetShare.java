package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record GetShare(String shareId, ActorRef<ShareReply> replyTo) implements UserCommand {
  public GetShare {
    if (shareId == null || shareId.isBlank()) {
      throw new IllegalArgumentException("shareId is required");
    }
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
