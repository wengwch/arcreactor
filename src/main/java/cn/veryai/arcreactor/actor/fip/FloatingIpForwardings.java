package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.*;
import org.openstack4j.api.exceptions.ResponseException;
import org.springframework.stereotype.Service;

/** Routes each request to one IP actor. Candidate selection belongs to the instance workflow. */
@Service
public final class FloatingIpForwardings {
  private final ActorSystem<?> system;
  private final ClusterSharding sharding;
  private final OpenStackClient client;
  private final Duration askTimeout;
  private final Cache<String, FloatingIpIdentity> identities = Caffeine.newBuilder().maximumSize(10000).build();

  public FloatingIpForwardings(ActorSystem<?> system, OpenStackClient client) {
    this.system = system; this.sharding = ClusterSharding.get(system); this.client = client;
    Config config = system.settings().config().getConfig("cloud.fip");
    askTimeout = config.getDuration("ask-timeout");
    FloatingIpSettings settings = new FloatingIpSettings(config.getDuration("retry-delay"),
        config.getDuration("operation-timeout"), config.getInt("max-attempts"));
    sharding.init(Entity.of(FloatingIpActor.TYPE_KEY,
        context -> FloatingIpActor.awaitingIdentity(context.getEntityId(), client, settings)));
  }

  public CompletionStage<ForwardingReply> create(String floatingIpId, String claimId, ForwardingSpec spec) {
    return entity(spec.regionId(), floatingIpId).thenCompose(ref ->
        AskPattern.<FloatingIpCommand, ForwardingReply>ask(ref,
            replyTo -> new CreatePortForwarding(claimId, spec, replyTo), askTimeout, system.scheduler()))
        .exceptionally(error -> {
          Throwable cause = rootCause(error);
          // Only failures known to precede actor submission can reject this candidate immediately.
          if (cause instanceof MissingFloatingIp || cause instanceof OpenStackOperationException
              || (cause instanceof ResponseException response && response.getStatus() >= 400
                  && response.getStatus() < 500 && response.getStatus() != 408 && response.getStatus() != 429)) {
            return new ForwardingReply(ForwardingReply.Status.REJECTED, null,
                "Floating IP identity could not be resolved");
          }
          throw new CompletionException(cause);
        });
  }
  public CompletionStage<ForwardingReply> get(String region, String floatingIpId, String claimId) {
    return entity(region, floatingIpId).thenCompose(ref -> AskPattern.<FloatingIpCommand, ForwardingReply>ask(ref,
        replyTo -> new GetPortForwarding(claimId, replyTo), askTimeout, system.scheduler()));
  }
  public CompletionStage<ForwardingReply> delete(String region, String floatingIpId, String claimId) {
    return entity(region, floatingIpId).thenCompose(ref -> AskPattern.<FloatingIpCommand, ForwardingReply>ask(ref,
        replyTo -> new DeletePortForwarding(claimId, replyTo), askTimeout, system.scheduler()));
  }
  public CompletionStage<Boolean> deleteInstance(String region, List<String> floatingIpIds, String instanceId) {
    List<CompletableFuture<Boolean>> deletions = floatingIpIds.stream().distinct().map(id ->
        entity(region, id).thenCompose(ref -> AskPattern.<FloatingIpCommand, Boolean>ask(ref,
            replyTo -> new DeleteInstancePortForwardings(instanceId, replyTo), askTimeout, system.scheduler()))
            .handle((done, error) -> {
              if (error == null) return Boolean.TRUE.equals(done);
              Throwable cause = rootCause(error);
              // A deleted floating IP cannot retain or accept forwarding rules.
              return cause instanceof MissingFloatingIp
                  || (cause instanceof ResponseException response && response.getStatus() == 404);
            }).toCompletableFuture()).toList();
    return CompletableFuture.allOf(deletions.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> deletions.stream().allMatch(CompletableFuture::join));
  }
  public CompletionStage<ForwardingReply> reconcile(String region, String floatingIpId, String claimId) {
    return entity(region, floatingIpId).thenCompose(ref -> AskPattern.<FloatingIpCommand, ForwardingReply>ask(ref,
        replyTo -> new ReconcilePortForwarding(claimId, replyTo), askTimeout, system.scheduler()));
  }

  private CompletionStage<EntityRef<FloatingIpCommand>> entity(String region, String floatingIpId) {
    String key = FloatingIpIdentity.entityId(region, floatingIpId);
    FloatingIpIdentity cached = identities.getIfPresent(key);
    CompletionStage<FloatingIpIdentity> identity = cached != null ? CompletableFuture.completedFuture(cached)
        : client.getFloatingIp(region, floatingIpId).thenApply(ip -> {
          if (ip == null) throw new MissingFloatingIp();
          if (!floatingIpId.equals(ip.getId()) || ip.getFloatingIpAddress() == null
              || ip.getFloatingIpAddress().isBlank() || ip.getTenantId() == null || ip.getTenantId().isBlank()) {
            throw new OpenStackOperationException("Floating IP identity is incomplete", null);
          }
          return new FloatingIpIdentity(region, floatingIpId, ip.getFloatingIpAddress(), ip.getTenantId());
        });
    return identity.thenApply(resolved -> {
      identities.put(key, resolved);
      EntityRef<FloatingIpCommand> ref = sharding.entityRefFor(FloatingIpActor.TYPE_KEY, key);
      ref.tell(new InitializeFloatingIp(resolved));
      return ref;
    });
  }

  private static Throwable rootCause(Throwable error) {
    while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
    return error;
  }
  private static final class MissingFloatingIp extends RuntimeException {}
}
