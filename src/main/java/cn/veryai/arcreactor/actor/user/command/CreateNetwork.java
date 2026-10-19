package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.model.NetworkSpec;
import cn.veryai.arcreactor.actor.user.reply.NetworkReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** One managed network and router per user and region; the project must already be READY. */
public record CreateNetwork(NetworkSpec spec, ActorRef<NetworkReply> replyTo) implements UserCommand {
  public CreateNetwork {
    Objects.requireNonNull(spec, "spec");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
