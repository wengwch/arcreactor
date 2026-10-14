package cn.veryai.arcreactor.actor.fip;

import cn.veryai.arcreactor.actor.fip.command.*;
import cn.veryai.arcreactor.actor.fip.event.*;
import cn.veryai.arcreactor.actor.fip.model.*;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim.Phase;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.openstack.NetProtocol;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackClient.ForwardingTarget;
import cn.veryai.arcreactor.openstack.OpenStackClient.ManagedNetworkResource;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.*;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.*;
import org.openstack4j.api.exceptions.ResponseException;

/** One region owns allocation across all its shared IPs and shared security-rule references. */
public final class FloatingIpActor
    extends EventSourcedBehavior<FloatingIpCommand, FloatingIpEvent, FloatingIpState> {
  public static final String ENTITY_TYPE = "FloatingIp";
  public static final EntityTypeKey<FloatingIpCommand> TYPE_KEY =
      EntityTypeKey.create(FloatingIpCommand.class, ENTITY_TYPE);
  private final String region;
  private final ActorContext<FloatingIpCommand> context;
  private final TimerScheduler<FloatingIpCommand> timers;
  private final OpenStackClient client;
  private final FloatingIpSettings settings;
  private final Set<String> delayed = new HashSet<>();
  private String activeClaim;
  private long activeGeneration;

  public record Drive() implements FloatingIpCommand {}
  public record ContinueClaim(String claimId, long generation) implements FloatingIpCommand {}
  public record StepResult(ForwardingTarget target, ManagedNetworkResource resource, boolean pending)
      implements java.io.Serializable {
    static StepResult done() { return new StepResult(null, null, false); }
  }
  public record StepCompleted(String claimId, long generation, StepResult result,
      boolean initialWrite, boolean definitive, String failure) implements FloatingIpCommand {}

  public static Behavior<FloatingIpCommand> create(String region, OpenStackClient client,
      FloatingIpSettings settings) {
    return Behaviors.setup(context -> Behaviors.withTimers(
        timers -> new FloatingIpActor(region, context, timers, client, settings)));
  }

  private FloatingIpActor(String region, ActorContext<FloatingIpCommand> context,
      TimerScheduler<FloatingIpCommand> timers, OpenStackClient client, FloatingIpSettings settings) {
    super(PersistenceId.of(ENTITY_TYPE, region));
    if (region == null || region.isBlank()) throw new IllegalArgumentException("region is required");
    this.region = region; this.context = context; this.timers = timers;
    this.client = Objects.requireNonNull(client); this.settings = Objects.requireNonNull(settings);
  }

  @Override public FloatingIpState emptyState() { return new FloatingIpState(Map.of(), Set.of(), Set.of()); }

  @Override public CommandHandler<FloatingIpCommand, FloatingIpEvent, FloatingIpState> commandHandler() {
    return newCommandHandlerBuilder().forAnyState()
        .onCommand(CreatePortForwarding.class, this::onCreate)
        .onCommand(GetPortForwarding.class, this::onGet)
        .onCommand(DeletePortForwarding.class, this::onDelete)
        .onCommand(DeleteInstancePortForwardings.class, this::onDeleteInstance)
        .onCommand(ReconcilePortForwarding.class, this::onReconcile)
        .onCommand(Drive.class, (state, command) -> { drive(state); return Effect().none(); })
        .onCommand(ContinueClaim.class, this::onContinue)
        .onCommand(StartStep.class, this::onStartStep)
        .onCommand(StepCompleted.class, this::onStepCompleted).build();
  }

  private Effect<FloatingIpEvent, FloatingIpState> onCreate(FloatingIpState state, CreatePortForwarding command) {
    ForwardingClaim existing = state.claims().get(command.claimId());
    if (existing != null) {
      command.replyTo().tell(existing.spec().equals(command.spec()) ? ForwardingReply.from(existing)
          : new ForwardingReply(ForwardingReply.Status.REJECTED, null, "claimId specification conflict"));
      return Effect().none();
    }
    if (!region.equals(command.spec().regionId()) || state.cancelledClaims().contains(command.claimId())
        || state.cancelledInstances().contains(command.spec().instanceId())) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, null,
          "Region mismatch or forwarding has been cancelled"));
      return Effect().none();
    }
    boolean duplicate = state.claims().values().stream().anyMatch(claim ->
        claim.phase() != Phase.DELETED && claim.phase() != Phase.FAILED
            && claim.spec().instanceId().equals(command.spec().instanceId())
            && claim.spec().portId().equals(command.spec().portId())
            && claim.spec().internalPort() == command.spec().internalPort());
    if (duplicate) {
      command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.REJECTED, null,
          "This instance service already has a forwarding claim"));
      return Effect().none();
    }
    long order = state.claims().values().stream().mapToLong(ForwardingClaim::order).max().orElse(0) + 1;
    ForwardingClaim claim = ForwardingClaim.requested(command.claimId(), command.spec(), order);
    return Effect().persist(new ForwardingUpdated(claim)).thenRun(next -> {
      command.replyTo().tell(ForwardingReply.from(claim)); drive(next);
    });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onGet(FloatingIpState state, GetPortForwarding command) {
    command.replyTo().tell(reply(state.claims().get(command.claimId())));
    return Effect().none();
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDelete(FloatingIpState state, DeletePortForwarding command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (claim == null) {
      return Effect().persist(new ForwardingCancelled(command.claimId(), null))
          .thenRun(next -> command.replyTo().tell(new ForwardingReply(ForwardingReply.Status.DELETED, null, null)));
    }
    if (claim.phase() == Phase.DELETED || (claim.deleteRequested() && !claim.paused())) {
      command.replyTo().tell(ForwardingReply.from(claim)); return Effect().none();
    }
    ForwardingClaim deleting = claim.requestDelete();
    return Effect().persist(new ForwardingUpdated(deleting)).thenRun(next -> {
      delayed.remove(claim.claimId()); command.replyTo().tell(ForwardingReply.from(deleting)); drive(next);
    });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onDeleteInstance(
      FloatingIpState state, DeleteInstancePortForwardings command) {
    List<FloatingIpEvent> events = new ArrayList<>();
    if (!state.cancelledInstances().contains(command.instanceId())) {
      events.add(new ForwardingCancelled(null, command.instanceId()));
    }
    boolean complete = true;
    for (ForwardingClaim claim : state.claims().values()) {
      if (!claim.spec().instanceId().equals(command.instanceId()) || claim.phase() == Phase.DELETED) continue;
      complete = false;
      if (!claim.deleteRequested() || claim.paused()) events.add(new ForwardingUpdated(claim.requestDelete()));
    }
    boolean reply = complete;
    return Effect().persist(events).thenRun(next -> {
      for (FloatingIpEvent event : events) {
        if (event instanceof ForwardingUpdated updated) delayed.remove(updated.claim().claimId());
      }
      command.replyTo().tell(reply); drive(next);
    });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onReconcile(
      FloatingIpState state, ReconcilePortForwarding command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (claim == null || !claim.paused()) {
      command.replyTo().tell(reply(claim)); return Effect().none();
    }
    ForwardingClaim resumed = claim.resume();
    return Effect().persist(new ForwardingUpdated(resumed)).thenRun(next -> {
      delayed.remove(claim.claimId()); command.replyTo().tell(ForwardingReply.from(resumed)); drive(next);
    });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onContinue(FloatingIpState state, ContinueClaim command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (claim != null && claim.generation() == command.generation()) delayed.remove(command.claimId());
    drive(state); return Effect().none();
  }

  private void drive(FloatingIpState state) {
    if (activeClaim != null) return;
    ForwardingClaim claim = state.claims().values().stream()
        .filter(c -> !c.terminal() && !c.paused() && !delayed.contains(c.claimId()))
        .min(Comparator.comparingLong(ForwardingClaim::order)).orElse(null);
    if (claim == null) return;
    // Intent is persisted in a command handler before any remote operation is started.
    context.getSelf().tell(new StartStep(claim.claimId(), claim.generation()));
  }

  public record StartStep(String claimId, long generation) implements FloatingIpCommand {}

  private Effect<FloatingIpEvent, FloatingIpState> onStartStep(FloatingIpState state, StartStep command) {
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (activeClaim != null || claim == null || claim.terminal() || claim.paused()
        || claim.generation() != command.generation() || delayed.contains(claim.claimId())) return Effect().none();
    if (claim.attempts() >= settings.maxAttempts()) {
      return Effect().persist(new ForwardingUpdated(claim.pause("Operation requires reconciliation")))
          .thenRun(this::drive);
    }
    boolean allowCreate = !claim.writeStarted();
    ForwardingClaim attempted = claim.attempted();
    return Effect().persist(new ForwardingUpdated(attempted))
        .thenRun(next -> execute(next, attempted, allowCreate));
  }

  private void execute(FloatingIpState state, ForwardingClaim claim, boolean allowCreate) {
    activeClaim = claim.claimId(); activeGeneration = claim.generation();
    CompletionStage<StepResult> operation;
    try { operation = performStep(state, claim, allowCreate); }
    catch (RuntimeException error) { operation = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(operation.toCompletableFuture().orTimeout(
        settings.operationTimeout().toMillis(), TimeUnit.MILLISECONDS), (result, error) -> {
      if (error == null) return new StepCompleted(claim.claimId(), claim.generation(), result, allowCreate, false, null);
      Throwable cause = error;
      while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
      boolean definitive = cause instanceof OpenStackOperationException && cause.getCause() == null;
      if (cause instanceof ResponseException response) {
        definitive = response.getStatus() >= 400 && response.getStatus() < 500
            && response.getStatus() != 408 && response.getStatus() != 429;
      }
      return new StepCompleted(claim.claimId(), claim.generation(), null, allowCreate, definitive,
          cause instanceof OpenStackOperationException && cause.getCause() == null ? cause.getMessage()
              : "Network operation failed: " + cause.getClass().getSimpleName()
                  + (cause instanceof ResponseException response ? " (HTTP " + response.getStatus() + ")" : ""));
    });
  }

  private Effect<FloatingIpEvent, FloatingIpState> onStepCompleted(FloatingIpState state, StepCompleted command) {
    if (Objects.equals(activeClaim, command.claimId()) && activeGeneration == command.generation()) activeClaim = null;
    ForwardingClaim claim = state.claims().get(command.claimId());
    if (claim == null || claim.generation() != command.generation()) {
      drive(state); return Effect().none();
    }
    if (command.failure() != null) {
      if (claim.phase() == Phase.ALLOCATING || (command.definitive() && !claim.deleting()
          && (!claim.writing() || command.initialWrite()))) {
        return save(claim.cleanup(!claim.deleteRequested(), command.failure()));
      }
      delay(claim); drive(state); return Effect().none();
    }
    if (command.result().pending()) {
      delay(claim); drive(state); return Effect().none();
    }
    ForwardingResources resources = claim.resources();
    StepResult result = command.result();
    ForwardingClaim next = switch (claim.phase()) {
      case ALLOCATING -> {
        int port = availablePort(state, claim, result.target().occupiedPorts());
        yield port == 0 ? claim.cleanup(true, "Floating IP port range exhausted")
            : claim.advance(Phase.CREATING_TCP, resources.allocated(result.target().floatingIpAddress(),
                result.target().securityGroupId(), port));
      }
      case CREATING_TCP -> claim.advance(Phase.CREATING_UDP, resources.forwarding(true, result.resource().id()));
      case CREATING_UDP -> claim.advance(Phase.ENSURING_TCP_RULE, resources.forwarding(false, result.resource().id()));
      case ENSURING_TCP_RULE -> claim.advance(Phase.ENSURING_UDP_RULE, resources.rule(true, result.resource()));
      case ENSURING_UDP_RULE -> claim.advance(Phase.READY, resources.rule(false, result.resource()));
      case DELETING_TCP -> claim.advance(Phase.DELETING_UDP, resources.forwarding(true, null));
      case DELETING_UDP -> claim.advance(Phase.DELETING_TCP_RULE, resources.forwarding(false, null));
      case DELETING_TCP_RULE -> claim.advance(Phase.DELETING_UDP_RULE, resources.rule(true, null));
      case DELETING_UDP_RULE -> {
        if (claim.deleteRequested()) yield claim.advance(Phase.DELETED, ForwardingResources.empty());
        if (claim.retryNextCandidate() && claim.candidateIndex() + 1 < claim.spec().floatingIpIds().size()) {
          yield claim.nextCandidate();
        }
        yield claim.advance(Phase.FAILED, ForwardingResources.empty());
      }
      case READY, FAILED, DELETED -> claim;
    };
    if (next.deleteRequested() && !next.deleting() && next.phase() != Phase.DELETED) {
      next = next.cleanup(false, next.failure());
    }
    return save(next);
  }

  private Effect<FloatingIpEvent, FloatingIpState> save(ForwardingClaim claim) {
    return Effect().persist(new ForwardingUpdated(claim)).thenRun(this::drive);
  }

  private void delay(ForwardingClaim claim) {
    delayed.add(claim.claimId());
    timers.startSingleTimer(claim.claimId(), new ContinueClaim(claim.claimId(), claim.generation()), settings.retryDelay());
  }

  private CompletionStage<StepResult> performStep(FloatingIpState state, ForwardingClaim claim, boolean allowCreate) {
    ForwardingSpec spec = claim.spec(); ForwardingResources r = claim.resources();
    NetProtocol protocol = switch (claim.phase()) {
      case CREATING_UDP, ENSURING_UDP_RULE, DELETING_UDP, DELETING_UDP_RULE -> NetProtocol.UDP;
      default -> NetProtocol.TCP;
    };
    return switch (claim.phase()) {
      case ALLOCATING -> client.prepareForwardingTarget(region, spec.projectId(), claim.floatingIpId(),
          spec.portId(), spec.fixedIp(), spec.securityGroupId()).thenApply(target -> new StepResult(target, null, false));
      case CREATING_TCP, CREATING_UDP -> client.ensurePortForwarding(region, claim.floatingIpId(),
          r.externalPort(), spec.fixedIp(), spec.internalPort(), spec.portId(), protocol,
          forwardingMarker(claim, protocol), allowCreate).thenApply(resource -> new StepResult(null, resource, resource == null));
      case ENSURING_TCP_RULE, ENSURING_UDP_RULE -> client.ensureForwardingSecurityRule(region, spec.projectId(),
          r.securityGroupId(), spec.internalPort(), protocol, securityMarker(claim, protocol), allowCreate)
          .thenApply(resource -> new StepResult(null, resource, resource == null));
      case DELETING_TCP, DELETING_UDP -> client.deleteManagedPortForwarding(region, claim.floatingIpId(),
          protocol == NetProtocol.TCP ? r.tcpForwardingId() : r.udpForwardingId(), forwardingMarker(claim, protocol))
          .thenApply(done -> new StepResult(null, null, !done));
      case DELETING_TCP_RULE, DELETING_UDP_RULE -> {
        boolean owned = protocol == NetProtocol.TCP ? r.tcpRuleOwned() : r.udpRuleOwned();
        String id = protocol == NetProtocol.TCP ? r.tcpRuleId() : r.udpRuleId();
        if (!owned || id == null || referencedByOtherClaim(state, claim, protocol, id)) {
          yield CompletableFuture.completedFuture(StepResult.done());
        }
        yield client.deleteManagedSecurityRule(region, spec.projectId(), id, securityMarker(claim, protocol))
            .thenApply(done -> new StepResult(null, null, !done));
      }
      case READY, FAILED, DELETED -> CompletableFuture.completedFuture(StepResult.done());
    };
  }

  private int availablePort(FloatingIpState state, ForwardingClaim claim, Set<Integer> remoteOccupied) {
    Set<Integer> occupied = new HashSet<>(remoteOccupied);
    state.claims().values().stream().filter(other -> !other.claimId().equals(claim.claimId())
        && other.floatingIpId().equals(claim.floatingIpId()) && other.resources().externalPort() > 0)
        .forEach(other -> occupied.add(other.resources().externalPort()));
    for (int port = settings.portRangeStart(); port <= settings.portRangeEnd(); port++) {
      if (!occupied.contains(port)) return port;
    }
    return 0;
  }

  private boolean referencedByOtherClaim(FloatingIpState state, ForwardingClaim claim,
      NetProtocol protocol, String ruleId) {
    return state.claims().values().stream().anyMatch(other -> !other.claimId().equals(claim.claimId())
        && other.phase() != Phase.DELETED && other.phase() != Phase.FAILED
        && other.spec().projectId().equals(claim.spec().projectId())
        && other.spec().internalPort() == claim.spec().internalPort()
        && Objects.equals(other.resources().securityGroupId(), claim.resources().securityGroupId())
        && (ruleId.equals(protocol == NetProtocol.TCP ? other.resources().tcpRuleId() : other.resources().udpRuleId())
            || (other.writeStarted() && other.phase() == (protocol == NetProtocol.TCP
                ? Phase.ENSURING_TCP_RULE : Phase.ENSURING_UDP_RULE))));
  }

  private String forwardingMarker(ForwardingClaim claim, NetProtocol protocol) {
    return marker("pf", region + ":" + claim.claimId() + ":" + claim.floatingIpId() + ":" + protocol);
  }
  private String securityMarker(ForwardingClaim claim, NetProtocol protocol) {
    return marker("sg", region + ":" + claim.spec().projectId() + ":" + claim.resources().securityGroupId()
        + ":" + claim.spec().internalPort() + ":" + protocol);
  }
  private static String marker(String kind, String value) {
    return "arcreactor:" + kind + ":" + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
  }
  private static ForwardingReply reply(ForwardingClaim claim) {
    return claim == null ? new ForwardingReply(ForwardingReply.Status.NOT_FOUND, null, null) : ForwardingReply.from(claim);
  }

  @Override public EventHandler<FloatingIpState, FloatingIpEvent> eventHandler() {
    return newEventHandlerBuilder().forAnyState()
        .onEvent(ForwardingUpdated.class, (state, event) -> state.updated(event.claim()))
        .onEvent(ForwardingCancelled.class, (state, event) -> state.cancelled(event.claimId(), event.instanceId())).build();
  }
  @Override public SignalHandler<FloatingIpState> signalHandler() {
    return newSignalHandlerBuilder().onSignal(RecoveryCompleted.class,
        (state, signal) -> context.getSelf().tell(new Drive())).build();
  }
  @Override public RetentionCriteria retentionCriteria() { return RetentionCriteria.snapshotEvery(100, 2); }
}
