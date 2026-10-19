package cn.veryai.arcreactor.actor.user;

import cn.veryai.arcreactor.actor.user.command.CreateProject;
import cn.veryai.arcreactor.actor.user.command.CreateShare;
import cn.veryai.arcreactor.actor.user.command.GetProject;
import cn.veryai.arcreactor.actor.user.command.GetShare;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.stereotype.Service;

/** Submits project and share requests to UserActor; query until READY before using the resources. */
@Service
public final class UserResources {
  private final ActorSystem<?> system;
  private final ClusterSharding sharding;
  private final Duration askTimeout;

  public UserResources(ActorSystem<?> system, OpenStackClient client) {
    this.system = system;
    this.sharding = ClusterSharding.get(system);
    Config config = system.settings().config().getConfig("cloud.user");
    this.askTimeout = config.getDuration("ask-timeout");
    UserShareSettings settings = new UserShareSettings(config.getDuration("share.poll-interval"),
        config.getInt("share.max-poll-attempts"), config.getDuration("share.operation-timeout"));
    UserProjectSettings projectSettings = new UserProjectSettings(config.getDuration("project.retry-delay"),
        config.getDuration("project.operation-timeout"), config.getInt("project.max-attempts"));
    sharding.init(Entity.of(UserActor.TYPE_KEY,
        context -> UserActor.create(context.getEntityId(), client, settings, projectSettings)));
  }

  public CompletionStage<ShareReply> createShare(String userId, String shareId, ShareSpec spec) {
    return AskPattern.<UserCommand, ShareReply>ask(entityRef(userId),
        replyTo -> new CreateShare(shareId, spec, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<ShareReply> getShare(String userId, String regionId) {
    return AskPattern.<UserCommand, ShareReply>ask(entityRef(userId),
        replyTo -> new GetShare(regionId, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<ProjectReply> createProject(String userId, String regionId, String name) {
    return AskPattern.<UserCommand, ProjectReply>ask(entityRef(userId),
        replyTo -> new CreateProject(regionId, name, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<ProjectReply> getProject(String userId, String regionId) {
    return AskPattern.<UserCommand, ProjectReply>ask(entityRef(userId),
        replyTo -> new GetProject(regionId, replyTo), askTimeout, system.scheduler());
  }

  private EntityRef<UserCommand> entityRef(String userId) {
    if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
    return sharding.entityRefFor(UserActor.TYPE_KEY, userId);
  }
}
