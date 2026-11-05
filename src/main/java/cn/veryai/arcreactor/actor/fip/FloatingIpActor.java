package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.event.*;
import cn.veryai.arcreactor.actor.fip.model.*;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim.Phase;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackClient.ForwardingTarget;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Supplier;
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
  private final OpenStackClient client;
  private final FloatingIpSettings settings;
  private String activeClaim;
  private String operationToken;

  public record Drive() implements FloatingIpCommand {}
  public record TargetReady(String claimId, long generation, ForwardingTarget target) implements FloatingIpCommand {}
  public record ForwardingReady(String claimId, long generation, ForwardingResources resources)
      implements FloatingIpCommand {}
  public record ForwardingFailed(String claimId, long generation, String failure, boolean uncertain)
      implements FloatingIpCommand {}
  public record StartDeletion(String claimId, long generation) implements FloatingIpCommand {}
  public record DeletionCompleted(String claimId, long generation) implements FloatingIpCommand {}
  public record DeletionFailed(String claimId, long generation, String failure) implements FloatingIpCommand {}

  /** Rejects late callbacks from another operation or an actor incarnation before recovery. */
  private record OperationCompleted(String token, FloatingIpCommand result) implements FloatingIpCommand {}

  public static Behavior<FloatingIpCommand> create(String entityId, OpenStackClient client,
      FloatingIpSettings settings) {
    return Behaviors.setup(context -> new FloatingIpActor(entityId, context, client, settings));
  }

  private FloatingIpActor(String entityId, ActorContext<FloatingIpCommand> context,
      OpenStackClient client, FloatingIpSettings settings) {
    super(PersistenceId.of(ENTITY_TYPE, entityId));
    this.entityId = entityId;
    this.context = context;
    this.client = Objects.requireNonNull(client);
    this.settings = Objects.requireNonNull(settings);
  }

  @Override public FloatingIpState emptyState() {
    return FloatingIpState.empty();
  }

  @Override public CommandHandler<FloatingIpCommand, FloatingIpEvent, FloatingIpState> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(EnableFloatingIp.class, this::onEnable)
        .onCommand(CreatePortForwarding.class, this::onCreate)
        .onCommand(GetPortForwarding.class, this::onGet)
        .onCommand(DeletePortForwarding.class, this::onDelete)
        .onCommand(DeleteInstancePortForwardings.class, this::onDeleteInstance)
        .onCommand(ReconcilePortForwarding.class, this::onReconcile)
        .onCommand(Drive.class, (state, command) -> { drive(state); return Effect().none(); })
        .onCommand(OperationCompleted.class, this::onOperationCompleted)
        .onCommand(TargetReady.class, this::onTargetReady)
        .onCommand(ForwardingReady.class, this::onReady)
        .onCommand(ForwardingFailed.class, this::onFailed)
        .onCommand(StartDeletion.class, this::onStartDeletion)
        .onCommand(DeletionCompleted.class, this::onDeleted)
        .onCommand(DeletionFailed.class, this::onDeletionFailed)
        .build();
  }

  private Effect<FloatingIpEvent, FloatingIpState> onEnable(FloatingIpState state, EnableFloatingIp command) {
    if (!entityId.equals(command.identity().entityId())) {
      command.replyTo().tell(false);
      return Effect().none();
    }
    byte[] portMask = command.portMask();
    if (state.enabled()) {
      command.replyTo().tell(state.identity().equals(command.identity())
          && BitSet.valueOf(state.initialPortMask()).equals(BitSet.valueOf(portMask)));
      return Effect().none();
    }
    return Effect().persist(new FloatingIpEnabled(
            command.identity(), portMask, state.enabled(command.identity(), portMask).usedPorts().toByteArray()))
        .thenRun(next -> command.replyTo().tell(true));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onCreate(FloatingIpState state, CreatePortForwarding command) {
    if (!state.enabled()) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, null, "Floating IP is not enabled"));
      return Effect().none();
    }
    ForwardingClaim existing = state.claims().get(command.claimId());
    if (existing != null) {
      command.replyTo().tell(existing.spec().equals(command.spec()) ? ForwardingReply.from(existing)
          : new ForwardingReply(ForwardingReply.Status.REJECTED, null, "claimId specification conflict"));
      return Effect().none();
    }
    if (!state.identity().regionId().equals(command.spec().regionId()) || state.cancelledClaims().contains(command.claimId())
        || state.cancelledInstances().contains(command.spec().instanceId())) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, null,
          "Region mismatch or forwarding has been cancelled"));
      return Effect().none();
    }
    boolean duplicate = state.claims().values().stream().anyMatch(claim ->
        claim.phase() != Phase.DELETED && (claim.phase() != Phase.FAILED || claim.ownsPort())
            && claim.spec().instanceId().equals(command.spec().instanceId())
            && claim.spec().internalPortId().equals(command.spec().internalPortId())
            && claim.spec().internalPort() == command.spec().internalPort());
    if (duplicate) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, null,
          "This instance service already has a forwarding claim"));
      return Effect().none();
    }
    long order = state.claims().values().stream().mapToLong(ForwardingClaim::order).max().orElse(0) + 1;
    return Effect().persist(new ForwardingCreationRequested(
            state.identity(), command.claimId(), command.spec(), order, deadline()))
        .thenRun(next -> {
          command.replyTo().tell(ForwardingReply.from(next.claims().get(command.claimId())));
          drive(next);
        });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onGet(FloatingIpState state, GetPortForwarding command) {
    command.replyTo().tell(reply(state.claims().get(command.claimId())));
    return Effect().none();
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDelete(FloatingIpState state, DeletePortForwarding command) {
    if (!state.enabled()) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.DELETED, null, null));
      return Effect().none();
    }
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (claim == null) {
      return Effect().persist(new ForwardingCancelled(state.identity(), command.claimId(), null))
          .thenRun(next -> command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.DELETED, null, null)));
    }
    if (claim.phase() == Phase.DELETED || (claim.deleteRequested() && !(claim.deleting() && claim.paused()))) {
      command.replyTo().tell(ForwardingReply.from(claim));
      return Effect().none();
    }
    List<FloatingIpEvent> events = new ArrayList<>();
    if (!claim.deleteRequested()) events.add(new ForwardingDeletionRequested(state.identity(), claim.claimId()));
    if (claim.deleting() && claim.paused()) {
      events.add(new ForwardingReconciliationRequested(state.identity(), claim.claimId(), deadline()));
    }
    return Effect().persist(events).thenRun(next -> {
      command.replyTo().tell(ForwardingReply.from(next.claims().get(claim.claimId())));
      drive(next);
    });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDeleteInstance(
      FloatingIpState state, DeleteInstancePortForwardings command) {
    if (!state.enabled()) {
      // No forwarding can have been accepted; this candidate has nothing to clean up.
      command.replyTo().tell(true);
      return Effect().none();
    }
    List<FloatingIpEvent> events = new ArrayList<>();
    if (!state.cancelledInstances().contains(command.instanceId())) {
      events.add(new ForwardingCancelled(state.identity(), null, command.instanceId()));
    }
    boolean complete = true;
    for (ForwardingClaim claim : state.claims().values()) {
      if (!claim.spec().instanceId().equals(command.instanceId()) || claim.phase() == Phase.DELETED) continue;
      complete = false;
      if (!claim.deleteRequested()) events.add(new ForwardingDeletionRequested(state.identity(), claim.claimId()));
      if (claim.deleting() && claim.paused() && settings.cleanupOnFailure()) {
        events.add(new ForwardingReconciliationRequested(state.identity(), claim.claimId(), deadline()));
      }
    }
    boolean reply = complete;
    return Effect().persist(events).thenRun(next -> {
      command.replyTo().tell(reply);
      drive(next);
    });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onReconcile(
      FloatingIpState state, ReconcilePortForwarding command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (claim == null || !claim.paused()) {
      command.replyTo().tell(reply(claim));
      return Effect().none();
    }
    return Effect().persist(new ForwardingReconciliationRequested(state.identity(), claim.claimId(), deadline()))
        .thenRun(next -> {
          command.replyTo().tell(ForwardingReply.from(next.claims().get(claim.claimId())));
          drive(next);
        });
  }

  private void drive(FloatingIpState state) {
    if (!state.enabled() || activeClaim != null) return;
    ForwardingClaim claim = state.claims().values().stream()
        .filter(c -> !c.paused() && c.phase() != Phase.DELETED)
        .filter(c -> c.deleteRequested() || c.deleting()
            ? cleanupAllowed(state, c)
            : c.phase() == Phase.CREATING || (c.phase() == Phase.FAILED && c.ownsPort() && settings.cleanupOnFailure()))
        .min(Comparator.comparingLong(ForwardingClaim::order)).orElse(null);
    if (claim == null) return;
    if (!claim.deleting() && (claim.deleteRequested() || claim.phase() == Phase.FAILED)) {
      context.getSelf().tell(new StartDeletion(claim.claimId(), claim.generation()));
    } else if (claim.deleting()) {
      executeDeletion(state.identity(), claim);
    } else {
      executeCreation(state.identity(), claim);
    }
  }

  private void executeCreation(FloatingIpIdentity identity, ForwardingClaim claim) {
    if (!claim.ownsPort()) {
      pipeOperation(claim,
          () -> client.prepareForwardingTarget(identity.regionId(), claim.spec().projectId(),
              identity.floatingIpId(), claim.spec().internalPortId(), claim.spec().fixedIp()),
          (target, error) -> error == null && target != null
              ? new TargetReady(claim.claimId(), claim.generation(), target)
              : new ForwardingFailed(claim.claimId(), claim.generation(), failure(error), false));
    } else {
      pipeOperation(claim,
          () -> client.createPortForwardings(identity, claim.claimId(), claim.spec(),
              claim.resources().externalPort(), claim.deadline()),
          (resources, error) -> error == null && completeResources(identity, claim, resources)
              ? new ForwardingReady(claim.claimId(), claim.generation(), resources)
              : new ForwardingFailed(claim.claimId(), claim.generation(), failure(error), !definitive(error)));
    }
  }

  private Effect<FloatingIpEvent, FloatingIpState> onTargetReady(FloatingIpState state, TargetReady command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.CREATING) || claim.ownsPort()) return Effect().none().thenRun(this::drive);
    if (claim.deleteRequested()) return Effect().persist(deletedEvent(state, claim)).thenRun(this::drive);
    if (!state.identity().floatingIp().equals(command.target().floatingIpAddress())) {
      return onFailed(state, new ForwardingFailed(claim.claimId(), claim.generation(),
          "Floating IP address changed from the actor identity", false));
    }
    if (!Instant.now().isBefore(claim.deadline())) {
      return onFailed(state, new ForwardingFailed(claim.claimId(), claim.generation(), "Forwarding creation timed out", false));
    }
    int port = availablePort(state, command.target().occupiedPorts());
    if (port == 0) {
      return onFailed(state, new ForwardingFailed(claim.claimId(), claim.generation(), "Floating IP port range exhausted", false));
    }
    ForwardingClaim allocated = claim.allocated(state.identity().floatingIp(), port);
    return Effect().persist(new ForwardingPortAllocated(
        state.identity(), claim.claimId(), port, state.updated(allocated).usedPorts().toByteArray())).thenRun(this::drive);
  }

  private Effect<FloatingIpEvent, FloatingIpState> onReady(FloatingIpState state, ForwardingReady command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.CREATING)) return Effect().none().thenRun(this::drive);
    List<FloatingIpEvent> events = new ArrayList<>();
    events.add(new ForwardingBecameReady(state.identity(), claim.claimId(), command.resources()));
    if (claim.deleteRequested() && cleanupAllowed(state, claim)) {
      events.add(new ForwardingDeletionStarted(state.identity(), claim.claimId(), deadline()));
    }
    return Effect().persist(events).thenRun(this::drive);
  }

  private Effect<FloatingIpEvent, FloatingIpState> onFailed(FloatingIpState state, ForwardingFailed command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.CREATING)) return Effect().none().thenRun(this::drive);
    List<FloatingIpEvent> events = new ArrayList<>();
    events.add(new ForwardingCreationFailed(state.identity(), claim.claimId(), command.failure(), command.uncertain()));
    if (!command.uncertain() && claim.ownsPort() && cleanupAllowed(state, claim)) {
      events.add(new ForwardingDeletionStarted(state.identity(), claim.claimId(), deadline()));
    }
    return Effect().persist(events).thenRun(this::drive);
  }

  private Effect<FloatingIpEvent, FloatingIpState> onStartDeletion(FloatingIpState state, StartDeletion command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (activeClaim != null || claim == null || claim.generation() != command.generation()
        || claim.paused() || claim.phase() == Phase.DELETED
        || !claim.deleteRequested() && claim.phase() != Phase.FAILED
        || !cleanupAllowed(state, claim)) return Effect().none();
    if (!claim.ownsPort()) return Effect().persist(deletedEvent(state, claim)).thenRun(this::drive);
    return Effect().persist(new ForwardingDeletionStarted(state.identity(), claim.claimId(), deadline())).thenRun(this::drive);
  }

  private void executeDeletion(FloatingIpIdentity identity, ForwardingClaim claim) {
    pipeOperation(claim,
        () -> client.deletePortForwardings(identity, claim.claimId(), claim.resources(), claim.deadline()),
        (deleted, error) -> error == null && Boolean.TRUE.equals(deleted)
            ? new DeletionCompleted(claim.claimId(), claim.generation())
            : new DeletionFailed(claim.claimId(), claim.generation(),
                error == null ? "OpenStack has not confirmed forwarding deletion" : failure(error)));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDeleted(FloatingIpState state, DeletionCompleted command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.DELETING)) return Effect().none().thenRun(this::drive);
    return Effect().persist(deletedEvent(state, claim)).thenRun(this::drive);
  }

  private ForwardingDeleted deletedEvent(FloatingIpState state, ForwardingClaim claim) {
    return new ForwardingDeleted(state.identity(), claim.claimId(), state.updated(claim.deleted()).usedPorts().toByteArray());
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDeletionFailed(FloatingIpState state, DeletionFailed command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.DELETING)) return Effect().none().thenRun(this::drive);
    return Effect().persist(new ForwardingDeletionFailed(state.identity(), claim.claimId(), command.failure())).thenRun(this::drive);
  }

  private <T> void pipeOperation(ForwardingClaim claim, Supplier<CompletionStage<T>> operation,
      BiFunction<T, Throwable, FloatingIpCommand> result) {
    activeClaim = claim.claimId();
    String token = UUID.randomUUID().toString();
    operationToken = token;
    Duration remaining = Duration.between(Instant.now(), claim.deadline());
    CompletionStage<T> future;
    try {
      future = remaining.isNegative() || remaining.isZero()
          ? CompletableFuture.failedFuture(new TimeoutException("Forwarding operation deadline exceeded"))
          : operation.get().toCompletableFuture().orTimeout(remaining.toNanos(), TimeUnit.NANOSECONDS);
    } catch (RuntimeException error) {
      future = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(future, (value, error) -> new OperationCompleted(token, result.apply(value, error)));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onOperationCompleted(
      FloatingIpState state, OperationCompleted command) {
    if (!Objects.equals(operationToken, command.token())) return Effect().none();
    activeClaim = null;
    operationToken = null;
    return switch (command.result()) {
      case TargetReady ready -> onTargetReady(state, ready);
      case ForwardingReady ready -> onReady(state, ready);
      case ForwardingFailed failed -> onFailed(state, failed);
      case DeletionCompleted deleted -> onDeleted(state, deleted);
      case DeletionFailed failed -> onDeletionFailed(state, failed);
      default -> throw new IllegalArgumentException("Unexpected forwarding operation result");
    };
  }

  private boolean cleanupAllowed(FloatingIpState state, ForwardingClaim claim) {
    return settings.cleanupOnFailure()
        || (claim.deleteRequested() && !state.cancelledInstances().contains(claim.spec().instanceId()));
  }

  private Instant deadline() { return Instant.now().plus(settings.operationTimeout()); }

  private static boolean matches(ForwardingClaim claim, long generation, Phase phase) {
    return claim != null && claim.generation() == generation && claim.phase() == phase;
  }

  private static boolean completeResources(FloatingIpIdentity identity, ForwardingClaim claim, ForwardingResources resources) {
    return resources != null && resources.externalPort() == claim.resources().externalPort()
        && identity.floatingIp().equals(resources.floatingIpAddress())
        && Objects.equals(claim.resources().floatingIpAddress(), resources.floatingIpAddress())
        && resources.tcpForwardingId() != null && !resources.tcpForwardingId().isBlank()
        && resources.udpForwardingId() != null && !resources.udpForwardingId().isBlank();
  }

  private static Throwable rootCause(Throwable error) {
    while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
    return error;
  }

  private static boolean definitive(Throwable error) {
    Throwable cause = rootCause(error);
    return cause instanceof OpenStackOperationException && cause.getCause() == null
        || cause instanceof ResponseException response && response.getStatus() >= 400 && response.getStatus() < 500
            && response.getStatus() != 408 && response.getStatus() != 429;
  }

  private static String failure(Throwable error) {
    Throwable cause = rootCause(error);
    if (cause == null) return "Forwarding operation returned incomplete resources";
    if (cause instanceof TimeoutException) return "Forwarding operation timed out; OpenStack outcome is unconfirmed";
    if (cause instanceof OpenStackOperationException && cause.getCause() == null) return cause.getMessage();
    return "Forwarding operation failed: " + cause.getClass().getSimpleName()
        + (cause instanceof ResponseException response ? " (HTTP " + response.getStatus() + ")" : "");
  }

  private static int availablePort(FloatingIpState state, Set<Integer> remoteOccupied) {
    BitSet occupied = state.usedPorts();
    for (int port : remoteOccupied) {
      if (port >= FloatingIpSettings.FIRST_PORT && port <= FloatingIpSettings.LAST_PORT) {
        occupied.set(port - FloatingIpSettings.FIRST_PORT);
      }
    }
    int index = occupied.nextClearBit(0);
    return index > FloatingIpSettings.LAST_PORT - FloatingIpSettings.FIRST_PORT
        ? 0 : FloatingIpSettings.FIRST_PORT + index;
  }

  private static ForwardingReply reply(ForwardingClaim claim) {
    return claim == null ? new ForwardingReply(ForwardingReply.Status.NOT_FOUND, null, null) : ForwardingReply.from(claim);
  }

  @Override public EventHandler<FloatingIpState, FloatingIpEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(FloatingIpEnabled.class, (state, event) -> state.enabled(event.identity(), event.portMask()))
        .onEvent(ForwardingCreationRequested.class, (state, event) ->
            state.updated(ForwardingClaim.requested(event.claimId(), event.identity().floatingIpId(),
                event.spec(), event.order(), event.deadline())))
        .onEvent(ForwardingPortAllocated.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).allocated(event.identity().floatingIp(), event.externalPort())))
        .onEvent(ForwardingBecameReady.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).ready(event.resources())))
        .onEvent(ForwardingCreationFailed.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).creationFailed(event.failure(), event.uncertain())))
        .onEvent(ForwardingDeletionRequested.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).requestDeletion()))
        .onEvent(ForwardingDeletionStarted.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).startDeletion(event.deadline())))
        .onEvent(ForwardingDeleted.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).deleted()))
        .onEvent(ForwardingDeletionFailed.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).deletionFailed(event.failure())))
        .onEvent(ForwardingReconciliationRequested.class, (state, event) ->
            state.updated(state.claims().get(event.claimId()).reconcile(event.deadline())))
        .onEvent(ForwardingCancelled.class, (state, event) -> state.cancelled(event.claimId(), event.instanceId()))
        .build();
  }

  @Override public SignalHandler<FloatingIpState> signalHandler() {
    return newSignalHandlerBuilder().onSignal(RecoveryCompleted.class, (state, signal) -> {
      activeClaim = null;
      operationToken = null;
      if (state.enabled()) context.getSelf().tell(new Drive());
    }).build();
  }

  @Override public RetentionCriteria retentionCriteria() { return RetentionCriteria.snapshotEvery(100, 2); }
}
