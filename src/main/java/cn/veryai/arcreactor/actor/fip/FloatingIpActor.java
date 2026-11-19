package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.event.*;
import cn.veryai.arcreactor.actor.fip.model.*;
import cn.veryai.arcreactor.actor.fip.model.FloatingIpState.Status;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim.Phase;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.*;
import org.openstack4j.api.exceptions.ResponseException;

/** One actor owns one floating IP and its external port reservations. */
public final class FloatingIpActor
    extends EventSourcedBehavior<FloatingIpCommand, FloatingIpEvent, FloatingIpState> {
  public static final String ENTITY_TYPE = "FloatingIp";
  public static final EntityTypeKey<FloatingIpCommand> TYPE_KEY =
      EntityTypeKey.create(FloatingIpCommand.class, ENTITY_TYPE);
  private final String entityId;
  private final ActorContext<FloatingIpCommand> context;
  private final TimerScheduler<FloatingIpCommand> timers;
  private final OpenStackClient client;
  private final FloatingIpSettings settings;
  // One in-flight operation per claim; different claims can progress independently.
  private final Map<String, String> operationTokens = new HashMap<>();

  public record ForwardingReady(String claimId, String token, long generation, ForwardingResources resources)
      implements FloatingIpCommand {}
  public record ForwardingFailed(String claimId, String token, long generation, String failure)
      implements FloatingIpCommand {}
  public record DeletionCompleted(String claimId, String token, long generation) implements FloatingIpCommand {}
  public record DeletionFailed(String claimId, String token, long generation, String failure) implements FloatingIpCommand {}
  public record DestructionCompleted() implements FloatingIpCommand {}
  /** A null error means OpenStack explicitly refused destruction. */
  public record DestructionFailed(Throwable error) implements FloatingIpCommand {}

  public static Behavior<FloatingIpCommand> create(String entityId, OpenStackClient client,
      FloatingIpSettings settings) {
    return Behaviors.setup(context -> Behaviors.withTimers(
        timers -> new FloatingIpActor(entityId, context, timers, client, settings)));
  }

  private FloatingIpActor(String entityId, ActorContext<FloatingIpCommand> context,
      TimerScheduler<FloatingIpCommand> timers, OpenStackClient client, FloatingIpSettings settings) {
    super(PersistenceId.of(ENTITY_TYPE, entityId));
    this.entityId = entityId;
    this.context = context;
    this.timers = timers;
    this.client = Objects.requireNonNull(client);
    this.settings = Objects.requireNonNull(settings);
  }

  @Override public FloatingIpState emptyState() {
    return FloatingIpState.empty();
  }

  @Override public CommandHandler<FloatingIpCommand, FloatingIpEvent, FloatingIpState> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(EnableFloatingIp.class, this::onEnable)
        .onCommand(DisableFloatingIp.class, this::onDisable)
        .onCommand(DestroyFloatingIp.class, this::onDestroy)
        .onCommand(GetFloatingIpState.class, this::onGetState)
        .onCommand(CreatePortForwarding.class, this::onCreate)
        .onCommand(GetPortForwarding.class, this::onGet)
        .onCommand(DeletePortForwarding.class, this::onDelete)
        .onCommand(CleanForwardingOutcomes.class, this::onCleanOutcomes)
        .onCommand(ForwardingReady.class, this::onReady)
        .onCommand(ForwardingFailed.class, this::onFailed)
        .onCommand(DeletionCompleted.class, this::onDeleted)
        .onCommand(DeletionFailed.class, this::onDeletionFailed)
        .onCommand(DestructionCompleted.class, this::onDestructionCompleted)
        .onCommand(DestructionFailed.class, this::onDestructionFailed)
        .build();
  }

  private Effect<FloatingIpEvent, FloatingIpState> onEnable(FloatingIpState state, EnableFloatingIp command) {
    if (state.status() == Status.DESTROYING || state.status() == Status.DESTROYED
        || !entityId.equals(command.identity().entityId())
        || state.identity() != null && !state.identity().equals(command.identity())) {
      command.replyTo().tell(false);
      return Effect().none();
    }
    if (state.status() == Status.ENABLED) {
      command.replyTo().tell(true);
      return Effect().none();
    }
    BitSet ports = state.usedPorts();
    ports.or(FloatingIpState.portMask(command.externalPorts()));
    return Effect().persist(new FloatingIpEnabled(command.identity(), ports.toByteArray()))
        .thenRun(next -> command.replyTo().tell(true));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDisable(FloatingIpState state, DisableFloatingIp command) {
    if (state.status() != Status.ENABLED) {
      command.replyTo().tell(true);
      return Effect().none();
    }
    return Effect().persist(new FloatingIpDisabled(state.identity(), state.usedPorts().toByteArray()))
        .thenRun(next -> command.replyTo().tell(true));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDestroy(FloatingIpState state, DestroyFloatingIp command) {
    if (state.status() == Status.DESTROYED || state.identity() == null) {
      command.replyTo().tell(true);
      return Effect().none();
    }
    if (state.status() != Status.DISABLED || !state.usedPorts().isEmpty()) {
      command.replyTo().tell(false);
      return Effect().none();
    }
    return Effect().persist(new FloatingIpDestructionStarted(state.identity()))
        .thenRun(next -> {
          command.replyTo().tell(true);
          executeDestruction(next.identity());
        });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onGetState(
      FloatingIpState state, GetFloatingIpState command) {
    command.replyTo().tell(state);
    return Effect().none();
  }

  private void executeDestruction(FloatingIpIdentity identity) {
    CompletionStage<Boolean> operation;
    try {
      operation = client.deleteFloatingIp(identity.regionId(), identity.osProjectId(), identity.osFloatingIpId());
    } catch (RuntimeException error) {
      operation = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(operation, (deleted, error) -> error == null && deleted
        ? new DestructionCompleted()
        : new DestructionFailed(error));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDestructionCompleted(
      FloatingIpState state, DestructionCompleted command) {
    if (state.status() != Status.DESTROYING) return Effect().none();
    return Effect().persist(new FloatingIpDestroyed(state.identity()));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDestructionFailed(
      FloatingIpState state, DestructionFailed command) {
    if (state.status() != Status.DESTROYING) return Effect().none();
    if (command.error() != null) {
      context.getLog().warn("Floating IP {} destruction failed: {}", entityId, failure(command.error()));
    }
    return Effect().persist(new FloatingIpDestructionFailed(state.identity()));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onCreate(FloatingIpState state, CreatePortForwarding command) {
    if (state.status() == Status.DESTROYING || state.status() == Status.DESTROYED) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, state.claims().get(command.claimId()),
          "Floating IP destruction has been requested"));
      return Effect().none();
    }
    ForwardingClaim existing = state.claims().get(command.claimId());
    if (existing != null) {
      command.replyTo().tell(existing.spec().equals(command.spec()) ? ForwardingReply.from(existing)
          : new ForwardingReply(ForwardingReply.Status.REJECTED, null, "claimId specification conflict"));
      return Effect().none();
    }
    ForwardingOutcome outcome = state.outcomes().get(command.claimId());
    if (outcome != null) {
      command.replyTo().tell(ForwardingReply.from(outcome));
      return Effect().none();
    }
    if (state.status() != Status.ENABLED) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, null, "Floating IP is not enabled"));
      return Effect().none();
    }
    if (!state.identity().regionId().equals(command.spec().regionId())) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, null,
          "Region mismatch"));
      return Effect().none();
    }
    boolean duplicate = state.claims().values().stream().anyMatch(claim ->
        claim.spec().instanceId().equals(command.spec().instanceId())
            && claim.spec().osInternalPortId().equals(command.spec().osInternalPortId())
            && claim.spec().internalPort() == command.spec().internalPort());
    if (duplicate) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, null,
          "This instance service already has a forwarding claim"));
      return Effect().none();
    }
    List<FloatingIpEvent> events = new ArrayList<>();
    events.add(new ForwardingCreationRequested(state.identity(), command.claimId(), command.spec()));
    int port = availablePort(state);
    if (port == 0) {
      events.add(new ForwardingCreationFailed(state.identity(), command.claimId(),
          "Floating IP port range exhausted", Instant.now()));
      return Effect().persist(events)
          .thenRun(next -> command.replyTo().tell(reply(next, command.claimId())));
    }
    ForwardingClaim allocated = ForwardingClaim.requested(command.claimId(), state.identity(),
        command.spec()).allocated(state.identity().floatingIp(), state.identity().domain(), port);
    events.add(new ForwardingPortAllocated(state.identity(), command.claimId(), port, state.status(),
        state.allocated(allocated).usedPorts().toByteArray()));
    return Effect().persist(events)
        .thenRun(next -> {
          command.replyTo().tell(ForwardingReply.from(next.claims().get(command.claimId())));
          executeCreation(next.identity(), next.claims().get(command.claimId()));
        });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onGet(FloatingIpState state, GetPortForwarding command) {
    command.replyTo().tell(reply(state, command.claimId()));
    return Effect().none();
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDelete(FloatingIpState state, DeletePortForwarding command) {
    String claimId = command.claimId();
    ActorRef<ForwardingReply> replyTo = command.replyTo();
    if (state.status() == Status.DESTROYING || state.status() == Status.DESTROYED) {
      replyTo.tell(reply(state, claimId));
      return Effect().none();
    }
    if (state.identity() == null) {
      replyTo.tell(new ForwardingReply(ForwardingReply.Status.DELETED, null, null));
      return Effect().none();
    }
    ForwardingClaim claim = state.claims().get(claimId);
    if (claim == null) {
      ForwardingOutcome outcome = state.outcomes().get(claimId);
      if (outcome != null && outcome.phase() == Phase.DELETED) {
        replyTo.tell(ForwardingReply.from(outcome));
        return Effect().none();
      }
      return Effect().persist(deletedEvent(state, claimId))
          .thenRun(next -> replyTo.tell(reply(next, claimId)));
    }
    if (claim.phase() == Phase.CREATING) {
      replyTo.tell(new ForwardingReply(ForwardingReply.Status.REJECTED, claim,
          "Forwarding is still being created; retry deletion after creation completes"));
      return Effect().none();
    }
    if (operationTokens.containsKey(claim.claimId())) {
      replyTo.tell(ForwardingReply.from(claim));
      return Effect().none();
    }
    return Effect().persist(new ForwardingDeletionStarted(state.identity(), claim.claimId()))
        .thenRun(next -> {
          replyTo.tell(reply(next, claim.claimId()));
          executeDeletion(next.identity(), next.claims().get(claim.claimId()));
        });
  }

  private void executeCreation(FloatingIpIdentity identity, ForwardingClaim claim) {
    String token = UUID.randomUUID().toString();
    pipeOperation(claim, token,
        () -> client.createPortForwardings(identity, claim.claimId(), claim.spec(),
            claim.resources().externalPort()),
        (resources, error) -> error == null
            ? new ForwardingReady(claim.claimId(), token, claim.generation(), resources)
            : new ForwardingFailed(claim.claimId(), token, claim.generation(), failure(error)));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onReady(FloatingIpState state, ForwardingReady command) {
    if (!operationTokens.remove(command.claimId(), command.token())) return Effect().none();
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.CREATING)) return Effect().none();
    if (!completeResources(state.identity(), claim, command.resources())) {
      return Effect().persist(new ForwardingCreationFailed(state.identity(), claim.claimId(), failure(null),
          Instant.now()));
    }
    return Effect().persist(new ForwardingBecameReady(state.identity(), claim.claimId(), command.resources()));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onFailed(FloatingIpState state, ForwardingFailed command) {
    if (!operationTokens.remove(command.claimId(), command.token())) return Effect().none();
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.CREATING)) return Effect().none();
    return Effect().persist(new ForwardingCreationFailed(state.identity(), claim.claimId(), command.failure(),
        Instant.now()));
  }

  private void executeDeletion(FloatingIpIdentity identity, ForwardingClaim claim) {
    String token = UUID.randomUUID().toString();
    pipeOperation(claim, token,
        () -> client.deletePortForwardings(identity, claim.claimId(), claim.spec(), claim.resources()),
        (ignored, error) -> error == null
            ? new DeletionCompleted(claim.claimId(), token, claim.generation())
            : new DeletionFailed(claim.claimId(), token, claim.generation(), failure(error)));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDeleted(FloatingIpState state, DeletionCompleted command) {
    if (!operationTokens.remove(command.claimId(), command.token())) return Effect().none();
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.DELETING)) return Effect().none();
    return Effect().persist(deletedEvent(state, claim.claimId()));
  }

  private ForwardingDeleted deletedEvent(FloatingIpState state, String claimId) {
    Instant deletedAt = Instant.now();
    return new ForwardingDeleted(state.identity(), claimId, state.status(),
        state.deleted(claimId, deletedAt).usedPorts().toByteArray(), deletedAt);
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDeletionFailed(FloatingIpState state, DeletionFailed command) {
    if (!operationTokens.remove(command.claimId(), command.token())) return Effect().none();
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.DELETING)) return Effect().none();
    return Effect().persist(new ForwardingDeletionFailed(state.identity(), claim.claimId(), command.failure()));
  }

  private <T> void pipeOperation(ForwardingClaim claim, String token, Supplier<CompletionStage<T>> operation,
      BiFunction<T, Throwable, FloatingIpCommand> result) {
    if (operationTokens.putIfAbsent(claim.claimId(), token) != null) return;
    CompletionStage<T> future;
    try {
      future = operation.get();
    } catch (RuntimeException error) {
      future = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(future, result::apply);
  }

  private static boolean matches(ForwardingClaim claim, long generation, Phase phase) {
    return claim != null && claim.generation() == generation && claim.phase() == phase;
  }

  private static boolean completeResources(FloatingIpIdentity identity, ForwardingClaim claim, ForwardingResources resources) {
    return resources != null && resources.externalPort() == claim.resources().externalPort()
        && identity.floatingIp().equals(resources.floatingIpAddress())
        && Objects.equals(claim.resources().floatingIpAddress(), resources.floatingIpAddress())
        && Objects.equals(claim.resources().domain(), resources.domain())
        && resources.osTcpForwardingId() != null && !resources.osTcpForwardingId().isBlank()
        && resources.osUdpForwardingId() != null && !resources.osUdpForwardingId().isBlank();
  }

  private static Throwable rootCause(Throwable error) {
    while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
    return error;
  }

  private static String failure(Throwable error) {
    Throwable cause = rootCause(error);
    if (cause == null) return "Forwarding operation returned incomplete resources";
    if (cause instanceof TimeoutException) return "OpenStack operation timed out; outcome is unconfirmed";
    if (cause instanceof OpenStackOperationException && cause.getCause() == null) return cause.getMessage();
    return "OpenStack operation failed: " + cause.getClass().getSimpleName()
        + (cause instanceof ResponseException response ? " (HTTP " + response.getStatus() + ")" : "");
  }

  private static int availablePort(FloatingIpState state) {
    BitSet occupied = state.usedPorts();
    int portCount = FloatingIpSettings.LAST_PORT - FloatingIpSettings.FIRST_PORT + 1;
    int start = ThreadLocalRandom.current().nextInt(portCount);
    int index = occupied.nextClearBit(start);
    if (index >= portCount) index = occupied.nextClearBit(0);
    return index >= portCount ? 0 : FloatingIpSettings.FIRST_PORT + index;
  }

  private static ForwardingReply reply(FloatingIpState state, String claimId) {
    if (state.status() == Status.DESTROYED) return new ForwardingReply(ForwardingReply.Status.DELETED, null, null);
    if (state.status() == Status.DESTROYING) return new ForwardingReply(ForwardingReply.Status.IN_PROGRESS, null,
        "Floating IP is being destroyed");
    ForwardingClaim claim = state.claims().get(claimId);
    if (claim != null) return ForwardingReply.from(claim);
    ForwardingOutcome outcome = state.outcomes().get(claimId);
    return outcome == null ? new ForwardingReply(ForwardingReply.Status.NOT_FOUND, null, null)
        : ForwardingReply.from(outcome);
  }

  private Effect<FloatingIpEvent, FloatingIpState> onCleanOutcomes(
      FloatingIpState state, CleanForwardingOutcomes command) {
    if (state.identity() == null) return Effect().none();
    Instant now = Instant.now();
    Instant cutoff = now.minus(settings.outcomeRetention());
    Set<String> claimIds = state.expiredOutcomes(cutoff);
    if (claimIds.isEmpty()) return Effect().none();
    return Effect().persist(new ForwardingOutcomesCleaned(state.identity(), claimIds, now));
  }

  @Override public EventHandler<FloatingIpState, FloatingIpEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(FloatingIpEnabled.class, (state, event) -> state.enabled(event.identity(), event.usedPortMask()))
        .onEvent(FloatingIpDisabled.class, (state, event) -> state.disabled())
        .onEvent(FloatingIpDestructionStarted.class, (state, event) -> state.destructionStarted())
        .onEvent(FloatingIpDestructionFailed.class, (state, event) -> state.disabled())
        .onEvent(FloatingIpDestroyed.class, (state, event) -> state.destructionCompleted())
        .onEvent(ForwardingCreationRequested.class, (state, event) ->
            state.updated(ForwardingClaim.requested(event.claimId(), event.identity(),
                event.spec())))
        .onEvent(ForwardingPortAllocated.class, (state, event) ->
            state.allocated(state.claims().get(event.claimId()).allocated(
                event.identity().floatingIp(), event.identity().domain(), event.externalPort())))
        .onEvent(ForwardingBecameReady.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).ready(event.resources())))
        .onEvent(ForwardingCreationFailed.class, (state, event) ->
            state.creationFailed(event.claimId(), event.failure(), event.failedAt()))
        .onEvent(ForwardingDeletionStarted.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).startDeletion()))
        .onEvent(ForwardingDeleted.class, (state, event) ->
            state.deleted(event.claimId(), event.deletedAt()))
        .onEvent(ForwardingDeletionFailed.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).deletionFailed(event.failure())))
        .onEvent(ForwardingOutcomesCleaned.class, (state, event) -> state.cleanOutcomes(event.claimIds()))
        .build();
  }

  @Override public SignalHandler<FloatingIpState> signalHandler() {
    return newSignalHandlerBuilder()
        .onSignal(RecoveryCompleted.class, (state, signal) -> recoverState(state))
        .build();
  }

  private void recoverState(FloatingIpState state) {
    operationTokens.clear();
    context.getSelf().tell(new CleanForwardingOutcomes());
    timers.startTimerWithFixedDelay(CleanForwardingOutcomes.class,
        new CleanForwardingOutcomes(), settings.outcomeCleanupInterval());
    if (state.identity() == null) return;
    if (state.status() == Status.DESTROYING) {
      executeDestruction(state.identity());
      return;
    }
    if (state.status() == Status.DESTROYED) return;
    state.claims().values().forEach(claim -> recoverClaim(state, claim));
  }

  /** Resume interrupted operations; failed operations still require an external delete command. */
  private void recoverClaim(FloatingIpState state, ForwardingClaim claim) {
    if (operationTokens.containsKey(claim.claimId())) return;
    switch (claim.phase()) {
      case CREATING -> executeCreation(state.identity(), claim);
      case DELETING -> executeDeletion(state.identity(), claim);
      default -> {}
    }
  }

  @Override public RetentionCriteria retentionCriteria() { return RetentionCriteria.snapshotEvery(100, 2); }
}
