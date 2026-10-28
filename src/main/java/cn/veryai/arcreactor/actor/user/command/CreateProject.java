package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** The user ID is UserActor's entity ID; a user has one managed project per OpenStack cluster. */
public record CreateProject(String clusterId, String name, ActorRef<ProjectReply> replyTo)
    implements UserCommand {
  public CreateProject {
    if (clusterId == null || clusterId.isBlank()) throw new IllegalArgumentException("clusterId is required");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
