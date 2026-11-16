package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.reply.UserCleanupReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record CleanUserResources(ActorRef<UserCleanupReply> replyTo) implements UserCommand {
  public CleanUserResources { Objects.requireNonNull(replyTo, "replyTo"); }
}
