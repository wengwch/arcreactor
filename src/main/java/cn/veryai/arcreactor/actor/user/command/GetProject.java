package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record GetProject(String regionId, ActorRef<ProjectReply> replyTo) implements UserCommand {
  public GetProject {
    if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId is required");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
