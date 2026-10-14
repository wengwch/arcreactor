package cn.veryai.arcreactor.actor.user;

import cn.veryai.arcreactor.actor.user.command.CreateShare;
import cn.veryai.arcreactor.actor.user.command.GetShare;
import cn.veryai.arcreactor.actor.user.command.UserCommand;
import cn.veryai.arcreactor.actor.user.model.ShareSpec;
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

/** Submission is acknowledged immediately; query until READY before generating cloud-init. */
@Service
public final class UserShares {
  private final ActorSystem<?> system;
  private final ClusterSharding sharding;
  private final Duration askTimeout;

  public UserShares(ActorSystem<?> system, OpenStackClient client) {
    this.system = system;
    this.sharding = ClusterSharding.get(system);
    Config config = system.settings().config().getConfig("cloud.user");
    this.askTimeout = config.getDuration("ask-timeout");
    UserShareSettings settings = new UserShareSettings(config.getDuration("share.poll-interval"),
        config.getInt("share.max-poll-attempts"), config.getDuration("share.operation-timeout"));
    sharding.init(Entity.of(UserActor.TYPE_KEY,
        context -> UserActor.create(context.getEntityId(), client, settings)));
  }

  public CompletionStage<ShareReply> createShare(String userId, String shareId, ShareSpec spec) {
    return AskPattern.<UserCommand, ShareReply>ask(entityRef(userId),
        replyTo -> new CreateShare(shareId, spec, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<ShareReply> getShare(String userId, String shareId) {
    return AskPattern.<UserCommand, ShareReply>ask(entityRef(userId),
        replyTo -> new GetShare(shareId, replyTo), askTimeout, system.scheduler());
  }

  private EntityRef<UserCommand> entityRef(String userId) {
    if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
    return sharding.entityRefFor(UserActor.TYPE_KEY, userId);
  }
}
