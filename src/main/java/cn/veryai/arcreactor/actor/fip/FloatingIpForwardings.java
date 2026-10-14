package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.*;
import org.springframework.stereotype.Service;

/** Create/delete acknowledge submission; query until READY/DELETED. IPs themselves are retained. */
@Service
public final class FloatingIpForwardings {
  private final ActorSystem<?> system;
  private final ClusterSharding sharding;
  private final Duration askTimeout;

  public FloatingIpForwardings(ActorSystem<?> system, OpenStackClient client) {
    this.system = system; this.sharding = ClusterSharding.get(system);
    Config config = system.settings().config().getConfig("cloud.fip");
    askTimeout = config.getDuration("ask-timeout");
    FloatingIpSettings settings = new FloatingIpSettings(config.getInt("port-range-start"),
        config.getInt("port-range-end"), config.getDuration("retry-delay"),
        config.getDuration("operation-timeout"), config.getInt("max-attempts"));
    sharding.init(Entity.of(FloatingIpActor.TYPE_KEY,
        context -> FloatingIpActor.create(context.getEntityId(), client, settings)));
  }

  public CompletionStage<ForwardingReply> create(String claimId, ForwardingSpec spec) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(spec.regionId()),
        replyTo -> new CreatePortForwarding(claimId, spec, replyTo), askTimeout, system.scheduler());
  }
  public CompletionStage<ForwardingReply> get(String region, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(region),
        replyTo -> new GetPortForwarding(claimId, replyTo), askTimeout, system.scheduler());
  }
  public CompletionStage<ForwardingReply> delete(String region, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(region),
        replyTo -> new DeletePortForwarding(claimId, replyTo), askTimeout, system.scheduler());
  }
  public CompletionStage<Boolean> deleteInstance(String region, String instanceId) {
    return AskPattern.<FloatingIpCommand, Boolean>ask(entity(region),
        replyTo -> new DeleteInstancePortForwardings(instanceId, replyTo), askTimeout, system.scheduler());
  }
  public CompletionStage<ForwardingReply> reconcile(String region, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(region),
        replyTo -> new ReconcilePortForwarding(claimId, replyTo), askTimeout, system.scheduler());
  }
  private EntityRef<FloatingIpCommand> entity(String region) {
    if (region == null || region.isBlank()) throw new IllegalArgumentException("region is required");
    return sharding.entityRefFor(FloatingIpActor.TYPE_KEY, region);
  }
}
