package cn.veryai.arcreactor.actor.user;

import cn.veryai.arcreactor.actor.user.command.CreateProject;
import cn.veryai.arcreactor.actor.user.command.GetProject;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.springframework.stereotype.Service;

/** Create acknowledges submission; query until READY before using the project. */
@Service
public final class UserProjects {
  private final ActorSystem<?> system;
  private final UserShares users;
  private final Duration askTimeout;

  public UserProjects(ActorSystem<?> system, UserShares users) {
    this.system = system;
    this.users = users;
    this.askTimeout = system.settings().config().getDuration("cloud.user.ask-timeout");
  }

  public CompletionStage<ProjectReply> createProject(String userId, String regionId, String name) {
    return AskPattern.<UserCommand, ProjectReply>ask(users.entityRef(userId),
        replyTo -> new CreateProject(regionId, name, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<ProjectReply> getProject(String userId, String regionId) {
    return AskPattern.<UserCommand, ProjectReply>ask(users.entityRef(userId),
        replyTo -> new GetProject(regionId, replyTo), askTimeout, system.scheduler());
  }
}
