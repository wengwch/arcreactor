package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.config.InstanceCreationProperties;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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

  public FloatingIpForwardings(ActorSystem<?> system, OpenStackClient client,
      InstanceCreationProperties creationProperties) {
    this.system = system;
    this.sharding = ClusterSharding.get(system);
    Config config = system.settings().config().getConfig("cloud.fip");
    askTimeout = config.getDuration("ask-timeout");
    FloatingIpSettings settings = new FloatingIpSettings(config.getDuration("operation-timeout"),
        creationProperties.isCleanupOnFailure());
    sharding.init(Entity.of(FloatingIpActor.TYPE_KEY,
        context -> FloatingIpActor.create(context.getEntityId(), client, settings)));
  }

  /** Completes after the actor persists its enabled state, identity and initial port reservations. */
  public CompletionStage<Boolean> enable(FloatingIpIdentity identity, byte[] portMask) {
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(portMask, "portMask");
    return AskPattern.<FloatingIpCommand, Boolean>ask(entity(identity.regionId(), identity.floatingIpId()),
        replyTo -> new EnableFloatingIp(identity, portMask, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<ForwardingReply> create(String floatingIpId, String claimId, ForwardingSpec spec) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(spec.regionId(), floatingIpId),
        replyTo -> new CreatePortForwarding(claimId, spec, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<ForwardingReply> get(String region, String floatingIpId, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(region, floatingIpId),
        replyTo -> new GetPortForwarding(claimId, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<ForwardingReply> delete(String region, String floatingIpId, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(region, floatingIpId),
        replyTo -> new DeletePortForwarding(claimId, replyTo), askTimeout, system.scheduler());
  }

  public CompletionStage<Boolean> deleteInstance(String region, List<String> floatingIpIds, String instanceId) {
    List<CompletableFuture<Boolean>> deletions = floatingIpIds.stream().distinct().map(id ->
        AskPattern.<FloatingIpCommand, Boolean>ask(entity(region, id),
            replyTo -> new DeleteInstancePortForwardings(instanceId, replyTo), askTimeout, system.scheduler())
            .handle((done, error) -> error == null && Boolean.TRUE.equals(done)).toCompletableFuture()).toList();
    return CompletableFuture.allOf(deletions.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> deletions.stream().allMatch(CompletableFuture::join));
  }

  public CompletionStage<ForwardingReply> reconcile(String region, String floatingIpId, String claimId) {
    return AskPattern.<FloatingIpCommand, ForwardingReply>ask(entity(region, floatingIpId),
        replyTo -> new ReconcilePortForwarding(claimId, replyTo), askTimeout, system.scheduler());
  }

  private EntityRef<FloatingIpCommand> entity(String region, String floatingIpId) {
    return sharding.entityRefFor(FloatingIpActor.TYPE_KEY, FloatingIpIdentity.entityId(region, floatingIpId));
  }
}
