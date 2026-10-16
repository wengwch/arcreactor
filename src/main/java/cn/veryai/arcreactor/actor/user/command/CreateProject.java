package cn.veryai.arcreactor.actor.user.command;

import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** The user ID is UserActor's entity ID; a user has one managed project per region. */
public record CreateProject(String regionId, String name, ActorRef<ProjectReply> replyTo)
    implements UserCommand {
  public CreateProject {
    if (regionId == null || regionId.isBlank()) throw new IllegalArgumentException("regionId is required");
    if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
