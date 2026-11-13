package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** Ensures one share per user/region; UserActor assigns the local share ID. */
public record CreateShare(ShareSpec spec, ActorRef<ShareReply> replyTo)
    implements UserCommand {
  public CreateShare {
    Objects.requireNonNull(spec, "spec");
    if (spec.osProjectId() == null || spec.osProjectId().isBlank()) {
      throw new IllegalArgumentException("osProjectId must be resolved before creating a share");
    }
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
