package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.model.PortForwardingSnapshot;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.*;
import org.springframework.stereotype.Service;

/** Routes each request to one IP actor. Candidate selection belongs to the instance workflow. */
@Service
public final class FloatingIpForwardings {
  private final ActorSystem<?> system;
  private final ClusterSharding sharding;
  private final Duration askTimeout;

  public FloatingIpForwardings(ActorSystem<?> system, OpenStackClient client) {
    this.system = system;
    this.sharding = ClusterSharding.get(system);
    Config config = system.settings().config().getConfig("cloud.fip");
    askTimeout = config.getDuration("ask-timeout");
    FloatingIpSettings settings = new FloatingIpSettings(
        config.getDuration("outcome-retention"), config.getDuration("outcome-cleanup-interval"));
    sharding.init(Entity.of(FloatingIpActor.TYPE_KEY,
        context -> FloatingIpActor.create(context.getEntityId(), client, settings)));
  }

  /** Port usage can be replaced only while disabled; omitted inputs preserve current reservations. */
  public CompletionStage<Boolean> enable(FloatingIpIdentity identity, byte[] portMask,
      List<PortForwardingSnapshot> forwardings) {
    Objects.requireNonNull(identity, "identity");
    return AskPattern.<FloatingIpCommand, Boolean>ask(entity(identity.regionId(), identity.floatingIpId()),
        replyTo -> new EnableFloatingIp(identity, portMask, forwardings, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<Boolean> disable(String region, String floatingIpId) {
    return AskPattern.<FloatingIpCommand, Boolean>ask(entity(region, floatingIpId),
        DisableFloatingIp::new, askTimeout, system.scheduler());
  }

  public CompletionStage<ForwardingReply> create(String floatingIpId, String claimId, ForwardingSpec spec) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(spec.regionId(), floatingIpId),
        replyTo -> new CreatePortForwarding(claimId, spec, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<ForwardingReply> get(String region, String floatingIpId, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(region, floatingIpId),
        replyTo -> new GetPortForwarding(claimId, replyTo), askTimeout, system.scheduler());
  }

  /** CREATING claims reject deletion; retry after creation completes and until the reply is DELETED. */
  public CompletionStage<ForwardingReply> delete(String region, String floatingIpId, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(region, floatingIpId),
        replyTo -> new DeletePortForwarding(claimId, replyTo), askTimeout, system.scheduler());
  }

  private EntityRef<FloatingIpCommand> entity(String region, String floatingIpId) {
    return sharding.entityRefFor(FloatingIpActor.TYPE_KEY, FloatingIpIdentity.entityId(region, floatingIpId));
  }
}
