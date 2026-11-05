package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.event.*;
import cn.veryai.arcreactor.actor.fip.model.*;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim.Phase;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Scheduler;
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
  // One in-flight operation per claim; different claims can progress independently.
  private final Map<String, String> operationTokens = new HashMap<>();

  private record ReservePort(String claimId, long generation, String token, Set<Integer> occupiedPorts,
      ActorRef<ForwardingReply> replyTo) implements FloatingIpCommand {}
  public record ForwardingReady(String claimId, long generation, ForwardingResources resources)
      implements FloatingIpCommand {}
  public record ForwardingFailed(String claimId, long generation, String failure, boolean uncertain)
      implements FloatingIpCommand {}
  public record StartDeletion(String claimId, long generation) implements FloatingIpCommand {}
  public record DeletionCompleted(String claimId, long generation) implements FloatingIpCommand {}
  public record DeletionFailed(String claimId, long generation, String failure) implements FloatingIpCommand {}

  /** Rejects late callbacks from another operation or an actor incarnation before recovery. */
  private record OperationCompleted(String claimId, String token, FloatingIpCommand result)
      implements FloatingIpCommand {}

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
        .onCommand(OperationCompleted.class, this::onOperationCompleted)
        .onCommand(ReservePort.class, this::onReservePort)
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
    return Effect().persist(new ForwardingCreationRequested(
            state.identity(), command.claimId(), command.spec(), deadline()))
        .thenRun(next -> {
          command.replyTo().tell(ForwardingReply.from(next.claims().get(command.claimId())));
          executeCreation(next.identity(), next.claims().get(command.claimId()));
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
      resumeClaim(next, claim.claimId());
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
      next.claims().values().stream()
          .filter(claim -> claim.spec().instanceId().equals(command.instanceId()))
          .forEach(claim -> resumeClaim(next, claim.claimId()));
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
          resumeClaim(next, claim.claimId());
        });
  }

  private void resumeClaim(FloatingIpState state, String claimId) {
    if (!state.enabled() || operationTokens.containsKey(claimId)) return;
    ForwardingClaim claim = state.claims().get(claimId);
    if (claim == null || claim.paused() || claim.phase() == Phase.DELETED) return;
    if (claim.deleteRequested() || claim.deleting()) {
      if (!cleanupAllowed(state, claim)) return;
      if (claim.deleting()) {
        executeDeletion(state.identity(), claim);
      } else {
        context.getSelf().tell(new StartDeletion(claimId, claim.generation()));
      }
    } else if (claim.phase() == Phase.CREATING) {
      executeCreation(state.identity(), claim);
    } else if (claim.phase() == Phase.FAILED && claim.ownsPort() && settings.cleanupOnFailure()) {
      context.getSelf().tell(new StartDeletion(claimId, claim.generation()));
    }
  }

  private void executeCreation(FloatingIpIdentity identity, ForwardingClaim claim) {
    ActorRef<FloatingIpCommand> self = context.getSelf();
    Scheduler scheduler = context.getSystem().scheduler();
    pipeOperation(claim, token -> {
      CompletionStage<ForwardingClaim> reservation = claim.ownsPort()
          ? CompletableFuture.completedFuture(claim)
          : client.prepareForwardingTarget(identity.regionId(), claim.spec().projectId(),
              identity.floatingIpId(), claim.spec().internalPortId(), claim.spec().fixedIp())
              .thenCompose(target -> {
                if (target == null || !identity.floatingIp().equals(target.floatingIpAddress())) {
                  return CompletableFuture.failedFuture(new OpenStackOperationException(
                      "Floating IP target is missing or its address changed from the actor identity", null));
                }
                Duration remaining = Duration.between(Instant.now(), claim.deadline());
                if (remaining.isNegative() || remaining.isZero()) {
                  return CompletableFuture.failedFuture(new TimeoutException("Forwarding creation timed out"));
                }
                return AskPattern.<FloatingIpCommand, ForwardingReply>ask(self,
                    replyTo -> new ReservePort(claim.claimId(), claim.generation(), token, target.occupiedPorts(), replyTo),
                    remaining, scheduler).thenApply(reply -> {
                      if (reply.status() != ForwardingReply.Status.IN_PROGRESS
                          || reply.claim() == null || !reply.claim().ownsPort()) {
                        throw new OpenStackOperationException(
                            reply.detail() == null ? "Forwarding port reservation was cancelled" : reply.detail(), null);
                      }
                      return reply.claim();
                    });
              });
      return reservation.thenCompose(reserved -> client.createPortForwardings(identity, claim.claimId(),
          claim.spec(), reserved.resources().externalPort(), claim.deadline()));
    }, (resources, error) -> error == null
        ? new ForwardingReady(claim.claimId(), claim.generation(), resources)
        : new ForwardingFailed(claim.claimId(), claim.generation(), failure(error), !definitive(error)));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onReservePort(FloatingIpState state, ReservePort command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!Objects.equals(operationTokens.get(command.claimId()), command.token())
        || !matches(claim, command.generation(), Phase.CREATING) || claim.ownsPort()) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, null,
          "Forwarding port reservation is no longer active"));
      return Effect().none();
    }
    if (claim.deleteRequested()) {
      return Effect().persist(deletedEvent(state, claim))
          .thenRun(next -> command.replyTo().tell(ForwardingReply.from(next.claims().get(claim.claimId()))));
    }
    String failure = Instant.now().isBefore(claim.deadline()) ? null : "Forwarding creation timed out";
    // Only the actor reads current reservations and persists a new port before the async flow continues.
    int port = failure == null ? availablePort(state, command.occupiedPorts()) : 0;
    if (failure == null && port == 0) failure = "Floating IP port range exhausted";
    if (failure != null) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.FAILED, null, failure));
      return Effect().none();
    }
    ForwardingClaim allocated = claim.allocated(state.identity().floatingIp(), port);
    return Effect().persist(new ForwardingPortAllocated(
        state.identity(), claim.claimId(), port, state.updated(allocated).usedPorts().toByteArray()))
        .thenRun(next -> command.replyTo().tell(ForwardingReply.from(next.claims().get(claim.claimId()))));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onReady(FloatingIpState state, ForwardingReady command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.CREATING)) return Effect().none();
    if (!completeResources(state.identity(), claim, command.resources())) {
      return onFailed(state, new ForwardingFailed(claim.claimId(), claim.generation(), failure(null), true));
    }
    List<FloatingIpEvent> events = new ArrayList<>();
    events.add(new ForwardingBecameReady(state.identity(), claim.claimId(), command.resources()));
    if (claim.deleteRequested() && cleanupAllowed(state, claim)) {
      events.add(new ForwardingDeletionStarted(state.identity(), claim.claimId(), deadline()));
    }
    return Effect().persist(events).thenRun(next -> resumeClaim(next, claim.claimId()));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onFailed(FloatingIpState state, ForwardingFailed command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.CREATING)) return Effect().none();
    List<FloatingIpEvent> events = new ArrayList<>();
    events.add(new ForwardingCreationFailed(state.identity(), claim.claimId(), command.failure(), command.uncertain()));
    if (!command.uncertain() && claim.ownsPort() && cleanupAllowed(state, claim)) {
      events.add(new ForwardingDeletionStarted(state.identity(), claim.claimId(), deadline()));
    }
    return Effect().persist(events).thenRun(next -> resumeClaim(next, claim.claimId()));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onStartDeletion(FloatingIpState state, StartDeletion command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (operationTokens.containsKey(command.claimId()) || claim == null || claim.generation() != command.generation()
        || claim.paused() || claim.phase() == Phase.DELETED
        || !claim.deleteRequested() && claim.phase() != Phase.FAILED
        || !cleanupAllowed(state, claim)) return Effect().none();
    if (!claim.ownsPort()) return Effect().persist(deletedEvent(state, claim));
    return Effect().persist(new ForwardingDeletionStarted(state.identity(), claim.claimId(), deadline()))
        .thenRun(next -> executeDeletion(next.identity(), next.claims().get(claim.claimId())));
  }

  private void executeDeletion(FloatingIpIdentity identity, ForwardingClaim claim) {
    pipeOperation(claim,
        token -> client.deletePortForwardings(identity, claim.claimId(), claim.resources(), claim.deadline()),
        (deleted, error) -> error == null && Boolean.TRUE.equals(deleted)
            ? new DeletionCompleted(claim.claimId(), claim.generation())
            : new DeletionFailed(claim.claimId(), claim.generation(),
                error == null ? "OpenStack has not confirmed forwarding deletion" : failure(error)));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDeleted(FloatingIpState state, DeletionCompleted command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.DELETING)) return Effect().none();
    return Effect().persist(deletedEvent(state, claim));
  }

  private ForwardingDeleted deletedEvent(FloatingIpState state, ForwardingClaim claim) {
    return new ForwardingDeleted(state.identity(), claim.claimId(), state.updated(claim.deleted()).usedPorts().toByteArray());
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDeletionFailed(FloatingIpState state, DeletionFailed command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (!matches(claim, command.generation(), Phase.DELETING)) return Effect().none();
    return Effect().persist(new ForwardingDeletionFailed(state.identity(), claim.claimId(), command.failure()));
  }

  private <T> void pipeOperation(ForwardingClaim claim, Function<String, CompletionStage<T>> operation,
      BiFunction<T, Throwable, FloatingIpCommand> result) {
    String token = UUID.randomUUID().toString();
    if (operationTokens.putIfAbsent(claim.claimId(), token) != null) return;
    Duration remaining = Duration.between(Instant.now(), claim.deadline());
    CompletionStage<T> future;
    try {
      future = remaining.isNegative() || remaining.isZero()
          ? CompletableFuture.failedFuture(new TimeoutException("Forwarding operation deadline exceeded"))
          : operation.apply(token).toCompletableFuture().orTimeout(remaining.toNanos(), TimeUnit.NANOSECONDS);
    } catch (RuntimeException error) {
      future = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(future,
        (value, error) -> new OperationCompleted(claim.claimId(), token, result.apply(value, error)));
  }

  private Effect<FloatingIpEvent, FloatingIpState> onOperationCompleted(
      FloatingIpState state, OperationCompleted command) {
    if (!operationTokens.remove(command.claimId(), command.token())) return Effect().none();
    return switch (command.result()) {
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
    int portCount = FloatingIpSettings.LAST_PORT - FloatingIpSettings.FIRST_PORT + 1;
    int start = ThreadLocalRandom.current().nextInt(portCount);
    int index = occupied.nextClearBit(start);
    if (index >= portCount) index = occupied.nextClearBit(0);
    return index >= portCount ? 0 : FloatingIpSettings.FIRST_PORT + index;
  }

  private static ForwardingReply reply(ForwardingClaim claim) {
    return claim == null ? new ForwardingReply(ForwardingReply.Status.NOT_FOUND, null, null) : ForwardingReply.from(claim);
  }

  @Override public EventHandler<FloatingIpState, FloatingIpEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(FloatingIpEnabled.class, (state, event) -> state.enabled(event.identity(), event.portMask()))
        .onEvent(ForwardingCreationRequested.class, (state, event) ->
            state.updated(ForwardingClaim.requested(event.claimId(), event.identity().floatingIpId(),
                event.spec(), event.deadline())))
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
      operationTokens.clear();
      if (state.enabled()) {
        state.claims().keySet().forEach(claimId -> resumeClaim(state, claimId));
      }
    }).build();
  }

  @Override public RetentionCriteria retentionCriteria() { return RetentionCriteria.snapshotEvery(100, 2); }
}
