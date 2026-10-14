package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** shareId is a stable logical resource ID, reused by all instances sharing this share. */
public record CreateShare(String shareId, ShareSpec spec, ActorRef<ShareReply> replyTo)
    implements UserCommand {
  public CreateShare {
    if (shareId == null || shareId.isBlank()) {
      throw new IllegalArgumentException("shareId is required");
    }
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
