package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.reply.UserCleanupReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record GetUserCleanup(ActorRef<UserCleanupReply> replyTo) implements UserCommand {
  public GetUserCleanup { Objects.requireNonNull(replyTo, "replyTo"); }
}
