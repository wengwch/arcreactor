package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record GetProject(String clusterId, ActorRef<ProjectReply> replyTo) implements UserCommand {
  public GetProject {
    if (clusterId == null || clusterId.isBlank()) throw new IllegalArgumentException("clusterId is required");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
