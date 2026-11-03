package cn.veryai.arcreactor.actor.user;

import cn.veryai.arcreactor.actor.user.command.CreateProject;
import cn.veryai.arcreactor.actor.user.command.CreateShare;
import cn.veryai.arcreactor.actor.user.command.GetProject;
import cn.veryai.arcreactor.actor.user.command.GetShare;
import cn.veryai.arcreactor.actor.user.command.CreateNetwork;
import cn.veryai.arcreactor.actor.user.command.GetNetwork;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.model.NetworkSpec;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.actor.user.reply.NetworkReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.Entity;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.springframework.stereotype.Service;

/**
 * Waits for persisted creation results for projects, networks and shares.
 */
@Slf4j
@Service
public final class UserResources {
  private final ActorSystem<?> system;
  private final ClusterSharding sharding;
  private final Duration askTimeout;
  private final Duration projectCreateTimeout;
  private final Duration networkCreateTimeout;
  private final Duration shareCreateTimeout;

  public UserResources(ActorSystem<?> system, OpenStackClient client) {
    this.system = system;
    this.sharding = ClusterSharding.get(system);
    Config config = system.settings().config().getConfig("cloud.user");
    this.askTimeout = config.getDuration("ask-timeout");
    UserShareSettings settings =
        new UserShareSettings(
            config.getDuration("share.poll-interval"),
            config.getDuration("share.operation-timeout"),
            config.getDuration("share.creation-timeout"));
    this.shareCreateTimeout = settings.creationTimeout().plus(askTimeout);
    UserProjectSettings projectSettings = new UserProjectSettings(config.getDuration("project.operation-timeout"));
    // Allow one operation plus persistence and reply overhead.
    this.projectCreateTimeout = projectSettings.operationTimeout().plus(askTimeout);
    UserNetworkSettings networkSettings = new UserNetworkSettings(config.getDuration("network.operation-timeout"));
    this.networkCreateTimeout = networkSettings.operationTimeout().plus(askTimeout);
    sharding.init(
        Entity.of(
            UserActor.TYPE_KEY,
            context -> UserActor.create(context.getEntityId(), client, settings,
                projectSettings, networkSettings)));
  }

  /** Waits until the export location and access key are ready, or creation has failed. */
  public CompletionStage<ShareReply> createShare(String userId, String shareId, ShareSpec spec) {
    return AskPattern.<UserCommand, ShareReply>ask(
        entityRef(userId),
        replyTo -> new CreateShare(shareId, spec, replyTo),
        shareCreateTimeout,
        system.scheduler());
  }

  public CompletionStage<ShareReply> getShare(String userId, String regionId) {
    return AskPattern.<UserCommand, ShareReply>ask(
        entityRef(userId),
        replyTo -> new GetShare(regionId, replyTo),
        askTimeout,
        system.scheduler());
  }

  /** Waits for the persisted READY/FAILED result; conflicting specifications are rejected immediately. */
  public CompletionStage<ProjectReply> createProject(String userId, String clusterId, String name) {
    return AskPattern.<UserCommand, ProjectReply>ask(
        entityRef(userId),
        replyTo -> new CreateProject(clusterId, name, replyTo),
        projectCreateTimeout,
        system.scheduler());
  }

  public CompletionStage<ProjectReply> getProject(String userId, String clusterId) {
    return AskPattern.<UserCommand, ProjectReply>ask(
        entityRef(userId),
        replyTo -> new GetProject(clusterId, replyTo),
        askTimeout,
        system.scheduler());
  }

  /**
   * Requires a READY cluster project and exactly one preset shared/external LOCAL network.
   * Each user has one managed network per region. Waits for the persisted READY/FAILED result;
   * a READY state contains the network, subnet, router and interface port IDs.
   */
  public CompletionStage<NetworkReply> createNetwork(String userId, String clusterId, NetworkSpec spec) {
    return AskPattern.<UserCommand, NetworkReply>ask(
        entityRef(userId),
        replyTo -> new CreateNetwork(clusterId, spec, replyTo),
        networkCreateTimeout,
        system.scheduler());
  }

  public CompletionStage<NetworkReply> getNetwork(String userId, String regionId) {
    return AskPattern.<UserCommand, NetworkReply>ask(
        entityRef(userId),
        replyTo -> new GetNetwork(regionId, replyTo),
        askTimeout,
        system.scheduler());
  }

  private EntityRef<UserCommand> entityRef(String userId) {
    if (userId == null || userId.isBlank())
      throw new IllegalArgumentException("userId is required");
    return sharding.entityRefFor(UserActor.TYPE_KEY, userId);
  }
}
