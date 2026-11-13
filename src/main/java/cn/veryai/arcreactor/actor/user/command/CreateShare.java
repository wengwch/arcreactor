package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** A shareId is supplied only for recovery; normal requests may retry a failed system share. */
public record CreateShare(String shareId, ShareSpec spec, ActorRef<ShareReply> replyTo)
    implements UserCommand {
  public CreateShare {
    Objects.requireNonNull(spec, "spec");
    if (shareId != null && shareId.isBlank()) throw new IllegalArgumentException("shareId must not be blank");
    if (spec.osProjectId() == null || spec.osProjectId().isBlank()) {
      throw new IllegalArgumentException("osProjectId must be resolved before creating a share");
    }
    Objects.requireNonNull(replyTo, "replyTo");
  }

  public CreateShare(ShareSpec spec, ActorRef<ShareReply> replyTo) {
    this(null, spec, replyTo);
  }
}
