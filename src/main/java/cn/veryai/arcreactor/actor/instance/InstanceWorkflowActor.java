package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.instance.command.CheckForwardingStatus;
import cn.veryai.arcreactor.actor.instance.command.CheckNetwork;
import cn.veryai.arcreactor.actor.instance.command.CheckProvisioning;
import cn.veryai.arcreactor.actor.instance.command.CompensationCompleted;
import cn.veryai.arcreactor.actor.instance.command.ConfirmRejected;
import cn.veryai.arcreactor.actor.instance.command.ConfirmSucceeded;
import cn.veryai.arcreactor.actor.instance.command.ConfirmUnknown;
import cn.veryai.arcreactor.actor.instance.command.CreateInstance;
import cn.veryai.arcreactor.actor.instance.command.ForwardingPreparationTimedOut;
import cn.veryai.arcreactor.actor.instance.command.ForwardingStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.GetInstanceState;
import cn.veryai.arcreactor.actor.instance.command.NetworkPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.ProvisioningCompleted;
import cn.veryai.arcreactor.actor.instance.command.RetryCompensation;
import cn.veryai.arcreactor.actor.instance.command.RetryConfirm;
import cn.veryai.arcreactor.actor.instance.command.SchedulingFailed;
import cn.veryai.arcreactor.actor.instance.command.SchedulingSucceeded;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.CompensationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCandidateSelected;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPrepared;
import cn.veryai.arcreactor.actor.instance.event.HypervisorSelected;
import cn.veryai.arcreactor.actor.instance.event.InstanceActivated;
import cn.veryai.arcreactor.actor.instance.event.InstanceCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceProvisioningFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.event.NetworkPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.NetworkPrepared;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationStarted;
import cn.veryai.arcreactor.actor.instance.event.NovaReachedActive;
import cn.veryai.arcreactor.actor.instance.event.NovaServerCreated;
import cn.veryai.arcreactor.actor.instance.event.ReservationConfirmedForInstance;
import cn.veryai.arcreactor.actor.instance.event.SchedulingStarted;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.actor.instance.model.PreparedShare;
import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.apache.pekko.persistence.typed.RecoveryCompleted;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.EventSourcedBehavior;
import org.apache.pekko.persistence.typed.javadsl.RetentionCriteria;
import org.apache.pekko.persistence.typed.javadsl.SignalHandler;

/** The sole Saga coordinator for one OpenStack instance. */
public final class InstanceWorkflowActor
    extends EventSourcedBehavior<WorkflowCommand, InstanceWorkflowEvent, InstanceWorkflowState> {
  public static final String ENTITY_TYPE = "InstanceWorkflow";
  public static final EntityTypeKey<WorkflowCommand> TYPE_KEY =
      EntityTypeKey.create(WorkflowCommand.class, ENTITY_TYPE);

  private static final String POLL_TIMER = "nova-status-poll";
  private static final String CONFIRM_TIMER = "reservation-confirm";
  private static final String COMPENSATION_TIMER = "compensation";
  private static final String FORWARDING_POLL_TIMER = "forwarding-status-poll";
  private static final String FORWARDING_DEADLINE_TIMER = "forwarding-preparation-deadline";
  private static final String NETWORK_TIMER = "network-preparation";
  private static final Executor CLOUD_INIT_EXECUTOR =
      task -> Thread.ofVirtual().name("instance-cloud-init").start(task);

  private final String instanceId;
  private final ActorContext<WorkflowCommand> context;
  private final TimerScheduler<WorkflowCommand> timers;
  private final HypervisorPlacement placement;
  private final OpenStackClient openStackClient;
  private final CloudInitRenderer cloudInitRenderer;
  private final FloatingIpForwardings floatingIpForwardings;
  private final InstanceWorkflowSettings settings;
  private final Clock clock;
  private final InstanceMetrics metrics = InstanceMetrics.NOOP;
  private String forwardingRequestToken;
  private String networkRequestToken;
  private String novaRequestToken;

  public static Behavior<WorkflowCommand> create(
      String instanceId,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceWorkflowSettings settings) {
    return create(instanceId, placement, openStackClient, cloudInitRenderer,
        floatingIpForwardings, settings, Clock.systemUTC());
  }

  static Behavior<WorkflowCommand> create(
      String instanceId,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceWorkflowSettings settings,
      Clock clock) {
    return Behaviors.setup(
        context ->
            Behaviors.withTimers(
                timers ->
                    new InstanceWorkflowActor(
                        instanceId, context, timers, placement, openStackClient,
                        cloudInitRenderer, floatingIpForwardings, settings, clock)));
  }

  private InstanceWorkflowActor(
      String instanceId,
      ActorContext<WorkflowCommand> context,
      TimerScheduler<WorkflowCommand> timers,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceWorkflowSettings settings,
      Clock clock) {
    super(PersistenceId.of(ENTITY_TYPE, instanceId));
    this.instanceId = requireText(instanceId, "instanceId");
    this.context = context;
    this.timers = timers;
    this.placement = Objects.requireNonNull(placement);
    this.openStackClient = Objects.requireNonNull(openStackClient);
    this.cloudInitRenderer = Objects.requireNonNull(cloudInitRenderer);
    this.floatingIpForwardings = Objects.requireNonNull(floatingIpForwardings);
    this.settings = Objects.requireNonNull(settings);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public InstanceWorkflowState emptyState() {
    return InstanceWorkflowState.empty(instanceId);
  }

  @Override
  public CommandHandler<WorkflowCommand, InstanceWorkflowEvent, InstanceWorkflowState>
      commandHandler() {
    return newCommandHandlerBuilder()
        .forAnyState()
        .onCommand(CreateInstance.class, this::onCreate)
        .onCommand(GetInstanceState.class, this::onGetState)
        .onCommand(CheckNetwork.class, this::onCheckNetwork)
        .onCommand(NetworkPreparationCompleted.class, this::onNetworkPreparationCompleted)
        .onCommand(CheckProvisioning.class, this::onCheckProvisioning)
        .onCommand(ProvisioningCompleted.class, this::onProvisioningCompleted)
        .onCommand(CheckForwardingStatus.class, this::onCheckForwardingStatus)
        .onCommand(ForwardingStatusCompleted.class, this::onForwardingStatusCompleted)
        .onCommand(ForwardingPreparationTimedOut.class, this::onForwardingPreparationTimedOut)
        .onCommand(SchedulingSucceeded.class, this::onSchedulingSucceeded)
        .onCommand(SchedulingFailed.class, this::onSchedulingFailed)
        .onCommand(ConfirmSucceeded.class, this::onConfirmSucceeded)
        .onCommand(ConfirmRejected.class, this::onConfirmRejected)
        .onCommand(ConfirmUnknown.class, this::onConfirmUnknown)
        .onCommand(RetryConfirm.class, this::onRetryConfirm)
        .onCommand(CompensationCompleted.class, this::onCompensationCompleted)
        .onCommand(RetryCompensation.class, this::onRetryCompensation)
        .build();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCreate(
      InstanceWorkflowState state, CreateInstance command) {
    if (!instanceId.equals(command.instanceId())) {
      command.replyTo().tell(InstanceReply.rejected("instanceId does not match entity ID"));
      return Effect().none();
    }
    if (state.phase() != InstancePhase.NEW) {
      boolean sameRequest =
          command.requestId().equals(state.requestId()) && command.spec().equals(state.spec());
      command
          .replyTo()
          .tell(
              sameRequest
                  ? InstanceReply.accepted(true)
                  : InstanceReply.rejected(
                      "instance already belongs to another request or specification"));
      return Effect().none();
    }
    Instant now = clock.instant();
    return Effect()
        .persist(
            List.of(
                new InstanceCreationRequested(instanceId, command.requestId(), command.spec(), now),
                new NetworkPreparationStarted(instanceId, now.plus(settings.provisioningTimeout()))))
        .thenRun(
            next -> {
              metrics.workflowStarted();
              command.replyTo().tell(InstanceReply.accepted(false));
              requestNetwork(next, true);
            });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onGetState(
      InstanceWorkflowState state, GetInstanceState command) {
    command.replyTo().tell(state);
    return Effect().none();
  }

  private static boolean hasText(String value) { return value != null && !value.isBlank(); }

  private InstanceWorkflowEvent afterNetworkStage(InstanceWorkflowState state, Instant now) {
    return state.spec().forwarding() == null
        ? new SchedulingStarted(instanceId, state.requestId(), 1, now)
        : new ForwardingPreparationStarted(instanceId, now.plus(settings.forwardingPreparationTimeout()));
  }

  private void startAfterNetwork(InstanceWorkflowState state) {
    if (state.phase() == InstancePhase.PREPARING_FORWARDING) startForwardingPreparation(state);
    else startScheduling(state);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckNetwork(
      InstanceWorkflowState state, CheckNetwork command) {
    if (state.phase() != InstancePhase.PREPARING_NETWORK || state.pollingGeneration() != command.generation()) return Effect().none();
    if (networkRequestToken == null) requestNetwork(state, false);
    else scheduleNetworkPoll(state);
    return Effect().none();
  }

  private void requestNetwork(InstanceWorkflowState state, boolean allowCreate) {
    String token = UUID.randomUUID().toString();
    networkRequestToken = token;
    CompletionStage<PreparedNetwork> request;
    try {
      request = openStackClient.prepareInstanceNetwork(target(state), networkOperationId(state), state.spec().network(), allowCreate);
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (resources, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new NetworkPreparationCompleted(state.pollingGeneration(), token, resources,
          cause instanceof OpenStackOperationException, cause == null ? null : cause.toString());
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNetworkPreparationCompleted(
      InstanceWorkflowState state, NetworkPreparationCompleted command) {
    if (state.phase() != InstancePhase.PREPARING_NETWORK || state.pollingGeneration() != command.generation()
        || !Objects.equals(networkRequestToken, command.requestToken())) return Effect().none();
    networkRequestToken = null;
    if (command.rejected()) return beginCompensation("NETWORK_PREPARATION_FAILED", command.detail());
    if (!clock.instant().isBefore(state.networkPreparation().deadline())) {
      return beginCompensation("NETWORK_PREPARATION_TIMEOUT", "Timed out preparing instance ports, QoS and trunk");
    }
    if (command.resources() == null) {
      scheduleNetworkPoll(state);
      return Effect().none();
    }
    return Effect().persist(List.of(new NetworkPrepared(instanceId, command.resources()), afterNetworkStage(state, clock.instant())))
        .thenRun(next -> { timers.cancel(NETWORK_TIMER); startAfterNetwork(next); });
  }

  private void scheduleNetworkPoll(InstanceWorkflowState state) {
    timers.startSingleTimer(NETWORK_TIMER, new CheckNetwork(state.pollingGeneration()), settings.pollInitialDelay());
  }

  private static String networkOperationId(InstanceWorkflowState state) {
    return "network:" + state.instanceId() + ":" + state.requestId();
  }

  private void startForwardingPreparation(InstanceWorkflowState state) {
    scheduleForwardingDeadline(state);
    if (!forwardingWaitExpired(state)) requestForwarding(state, 1, true);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckForwardingStatus(
      InstanceWorkflowState state, CheckForwardingStatus command) {
    if (!matchesForwarding(state, command.generation()) || forwardingRequestToken != null) return Effect().none();
    if (forwardingWaitExpired(state)) return beginCompensation("FORWARDING_PREPARATION_TIMEOUT", "Timed out preparing port forwarding");
    requestForwarding(state, command.attempt(), false);
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onForwardingStatusCompleted(
      InstanceWorkflowState state, ForwardingStatusCompleted command) {
    if (!matchesForwarding(state, command.generation())
        || !Objects.equals(forwardingRequestToken, command.requestToken())) return Effect().none();
    forwardingRequestToken = null;
    if (forwardingWaitExpired(state)) return beginCompensation("FORWARDING_PREPARATION_TIMEOUT", "Timed out preparing port forwarding");
    ForwardingReply reply = command.reply();
    if (reply == null) {
      scheduleForwardingPoll(state, command.attempt() + 1); return Effect().none();
    }
    return switch (reply.status()) {
      case READY -> {
        ForwardingClaim claim = reply.claim();
        if (claim == null || claim.phase() != ForwardingClaim.Phase.READY || claim.deleteRequested()
            || !forwardingClaimId(state).equals(claim.claimId()) || !forwardingSpec(state).equals(claim.spec())
            || !forwardingFloatingIpId(state).equals(claim.floatingIpId())
            || !hasText(claim.resources().tcpForwardingId()) || !hasText(claim.resources().udpForwardingId())
            || !hasText(claim.resources().tcpRuleId()) || !hasText(claim.resources().udpRuleId())
            || !hasText(claim.resources().floatingIpAddress()) || claim.resources().externalPort() < 10000
            || claim.resources().externalPort() > 40000) {
          yield beginCompensation("FORWARDING_RESULT_INVALID", "Incomplete or mismatched port forwarding result");
        }
        Instant now = clock.instant();
        yield Effect().persist(List.of(new ForwardingPrepared(instanceId, PreparedForwarding.from(claim), now),
                new SchedulingStarted(instanceId, state.requestId(), 1, now)))
            .thenRun(next -> { cancelForwardingTimers(); startScheduling(next); });
      }
      case FAILED, REJECTED, DELETED -> {
        int nextCandidate = state.forwardingCandidateIndex() + 1;
        if (nextCandidate >= state.spec().forwarding().floatingIpIds().size()) {
          yield beginCompensation("FORWARDING_PREPARATION_FAILED", "All floating IP candidates failed: " + reply.detail());
        }
        yield Effect().persist(new ForwardingCandidateSelected(instanceId, nextCandidate))
            .thenRun(this::startForwardingPreparation);
      }
      case NOT_FOUND -> {
        if (command.submission()) {
          scheduleForwardingPoll(state, command.attempt() + 1); yield Effect().none();
        }
        requestForwarding(state, command.attempt(), true);
        yield Effect().none();
      }
      case IN_PROGRESS, NEEDS_RECONCILIATION -> {
        scheduleForwardingPoll(state, command.attempt() + 1); yield Effect().none();
      }
    };
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onForwardingPreparationTimedOut(
      InstanceWorkflowState state, ForwardingPreparationTimedOut command) {
    if (state.phase() != InstancePhase.PREPARING_FORWARDING
        || !Objects.equals(state.forwardingPreparationDeadline(), command.deadline())) return Effect().none();
    if (!forwardingWaitExpired(state)) { scheduleForwardingDeadline(state); return Effect().none(); }
    return beginCompensation("FORWARDING_PREPARATION_TIMEOUT", "Timed out preparing port forwarding");
  }

  private void requestForwarding(InstanceWorkflowState state, int attempt, boolean submission) {
    String token = UUID.randomUUID().toString();
    forwardingRequestToken = token;
    CompletionStage<ForwardingReply> request;
    try {
      request = submission ? floatingIpForwardings.create(forwardingFloatingIpId(state), forwardingClaimId(state), forwardingSpec(state))
          : floatingIpForwardings.get(state.spec().region(), forwardingFloatingIpId(state), forwardingClaimId(state));
    } catch (RuntimeException error) { request = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(request, (reply, error) -> new ForwardingStatusCompleted(
        state.pollingGeneration(), token, attempt, submission, error == null ? reply : null));
  }

  private ForwardingSpec forwardingSpec(InstanceWorkflowState state) {
    PreparedNetwork network = state.networkPreparation().resources();
    var required = state.spec().forwarding();
    return new ForwardingSpec(instanceId, state.spec().share().userId(), state.spec().region(),
        state.projectId(), network.parentPortId(), network.parentFixedIp(), required.internalPort(), required.securityGroupId());
  }
  private static String forwardingFloatingIpId(InstanceWorkflowState state) {
    return state.spec().forwarding().floatingIpIds().get(state.forwardingCandidateIndex());
  }
  private static String forwardingClaimId(InstanceWorkflowState state) {
    return "fip:" + state.instanceId() + ":" + state.requestId();
  }
  private static boolean matchesForwarding(InstanceWorkflowState state, long generation) {
    return state.phase() == InstancePhase.PREPARING_FORWARDING && state.pollingGeneration() == generation;
  }
  private boolean forwardingWaitExpired(InstanceWorkflowState state) {
    return !clock.instant().isBefore(state.forwardingPreparationDeadline());
  }
  private void scheduleForwardingPoll(InstanceWorkflowState state, int attempt) {
    timers.startSingleTimer(FORWARDING_POLL_TIMER,
        new CheckForwardingStatus(state.pollingGeneration(), attempt), settings.pollDelay(attempt));
  }
  private void scheduleForwardingDeadline(InstanceWorkflowState state) {
    Duration remaining = Duration.between(clock.instant(), state.forwardingPreparationDeadline());
    timers.startSingleTimer(FORWARDING_DEADLINE_TIMER,
        new ForwardingPreparationTimedOut(state.forwardingPreparationDeadline()),
        remaining.isNegative() || remaining.isZero() ? Duration.ofMillis(1) : remaining);
  }
  private void cancelForwardingTimers() {
    timers.cancel(FORWARDING_POLL_TIMER); timers.cancel(FORWARDING_DEADLINE_TIMER);
    forwardingRequestToken = null;
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSchedulingSucceeded(
      InstanceWorkflowState state, SchedulingSucceeded command) {
    if (state.phase() != InstancePhase.SCHEDULING) return Effect().none();
    HypervisorPlacement.ScheduleResult result = command.result();
    if (result.status() == HypervisorPlacement.ScheduleResult.Status.NO_VALID_HOST) {
      return beginCompensation("NO_VALID_HOST", result.detail());
    }
    String reservationId = reservationId(state);
    String operationId = operationId(state);
    Instant now = clock.instant();
    return Effect()
        .persist(
            List.of(
                new HypervisorSelected(
                    instanceId, result.hypervisorId(), reservationId, result.attempts(), now),
                new NovaCreationStarted(
                    instanceId, operationId, now, now.plus(settings.provisioningTimeout()))))
        .thenRun(next -> requestProvisioning(next, true));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSchedulingFailed(
      InstanceWorkflowState state, SchedulingFailed command) {
    if (state.phase() != InstancePhase.SCHEDULING) return Effect().none();
    return beginCompensation("SCHEDULING_FAILED", command.reason());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckProvisioning(
      InstanceWorkflowState state, CheckProvisioning command) {
    if (state.phase() != InstancePhase.PROVISIONING || state.pollingGeneration() != command.generation()) return Effect().none();
    if (novaRequestToken == null) requestProvisioning(state, false);
    else scheduleProvisioningPoll(state);
    return Effect().none();
  }

  private void requestProvisioning(InstanceWorkflowState state, boolean submission) {
    String token = UUID.randomUUID().toString();
    novaRequestToken = token;
    CompletionStage<OpenStackClient.NovaServer> request;
    try {
      if (submission) request = submitNova(state);
      else if (state.novaServerId() != null) request = openStackClient.getServer(target(state), state.novaServerId());
      else request = openStackClient.findServerByOperationId(target(state), state.novaOperationId())
          .thenApply(OpenStackClient.ServerLookupResult::server);
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (server, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new ProvisioningCompleted(state.pollingGeneration(), token, server,
          submission && (cause instanceof OpenStackOperationException || cause instanceof CloudInitRenderer.RenderingException),
          cause == null ? null : cause.toString());
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onProvisioningCompleted(
      InstanceWorkflowState state, ProvisioningCompleted command) {
    if (state.phase() != InstancePhase.PROVISIONING || state.pollingGeneration() != command.generation()
        || !Objects.equals(novaRequestToken, command.requestToken())) return Effect().none();
    novaRequestToken = null;
    if (command.rejected()) return beginCompensation("NOVA_CREATE_REJECTED", command.detail());
    if (command.detail() != null) {
      // Even after the deadline, resolve uncertain writes before releasing capacity or deleting ports.
      scheduleProvisioningPoll(state);
      return Effect().none();
    }
    OpenStackClient.NovaServer server = command.server();
    boolean expired = !clock.instant().isBefore(state.provisioningDeadline());
    if (server == null) {
      if (expired) return beginCompensation("PROVISIONING_TIMEOUT", "Nova server was not found before deadline");
      scheduleProvisioningPoll(state);
      return Effect().none();
    }
    List<InstanceWorkflowEvent> events = new ArrayList<>();
    Instant now = clock.instant();
    if (!server.serverId().equals(state.novaServerId()) || server.status() != state.novaStatus()) {
      events.add(new NovaServerCreated(instanceId, state.novaOperationId(), server.serverId(), server.status(), now));
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ACTIVE) {
      events.add(new NovaReachedActive(instanceId, server.serverId(), now));
      return Effect().persist(events).thenRun(this::startConfirm);
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ERROR || expired) {
      events.add(new CompensationStarted(instanceId, expired ? "PROVISIONING_TIMEOUT" : "NOVA_ERROR",
          expired ? "Nova server did not become ACTIVE before deadline" : server.fault(), now));
      return Effect().persist(events).thenRun(this::startCompensation);
    }
    if (events.isEmpty()) {
      scheduleProvisioningPoll(state);
      return Effect().none();
    }
    return Effect().persist(events).thenRun(this::scheduleProvisioningPoll);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onConfirmSucceeded(
      InstanceWorkflowState state, ConfirmSucceeded command) {
    if (state.phase() != InstancePhase.CONFIRMING) return Effect().none();
    Instant now = clock.instant();
    return Effect()
        .persist(
            List.of(
                new ReservationConfirmedForInstance(
                    instanceId, state.hypervisorId(), state.reservationId(), now),
                new InstanceActivated(instanceId, state.novaServerId(), state.hypervisorId(), now)))
        .thenRun(next -> metrics.workflowActive());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onConfirmRejected(
      InstanceWorkflowState state, ConfirmRejected command) {
    if (state.phase() != InstancePhase.CONFIRMING) return Effect().none();
    return beginCompensation("RESERVATION_CONFIRM_REJECTED", command.reason());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onConfirmUnknown(
      InstanceWorkflowState state, ConfirmUnknown command) {
    if (state.phase() == InstancePhase.CONFIRMING) scheduleConfirmRetry(state);
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onRetryConfirm(
      InstanceWorkflowState state, RetryConfirm command) {
    if (state.phase() == InstancePhase.CONFIRMING
        && state.pollingGeneration() == command.generation()) {
      startConfirm(state);
    }
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCompensationCompleted(
      InstanceWorkflowState state, CompensationCompleted command) {
    if (state.phase() != InstancePhase.COMPENSATING
        || state.pollingGeneration() != command.generation()) {
      return Effect().none();
    }
    if (!command.success()) {
      scheduleCompensationRetry(state);
      return Effect().none();
    }
    return Effect()
        .persist(
            new InstanceProvisioningFailed(
                instanceId, state.failureCode(), state.failureMessage(), clock.instant()))
        .thenRun(next -> metrics.workflowFailed());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onRetryCompensation(
      InstanceWorkflowState state, RetryCompensation command) {
    if (state.phase() == InstancePhase.COMPENSATING
        && state.pollingGeneration() == command.generation()) {
      startCompensation(state);
    }
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> beginCompensation(
      String code, String message) {
    return Effect()
        .persist(new CompensationStarted(instanceId, code, message, clock.instant()))
        .thenRun(
            next -> {
              metrics.compensation();
              timers.cancel(NETWORK_TIMER);
              timers.cancel(POLL_TIMER);
              cancelForwardingTimers();
              startCompensation(next);
            });
  }

  private void startScheduling(InstanceWorkflowState state) {
    InstanceSpec spec = state.spec();
    HypervisorPlacement.Resources resources =
        new HypervisorPlacement.Resources(spec.vcpus(), spec.memoryGb(), spec.gpuCount());
    var request =
        new HypervisorPlacement.ScheduleRequest(
            state.requestId(),
            reservationId(state),
            instanceId,
            resources,
            settings.reservationTtl(),
            null,
            spec.region(),
            spec.hypervisorType());
    context.pipeToSelf(
        placement.schedule(request),
        (result, error) ->
            error == null
                ? new SchedulingSucceeded(result)
                : new SchedulingFailed(rootCause(error).toString()));
  }

  private CompletionStage<OpenStackClient.NovaServer> submitNova(InstanceWorkflowState state) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return cloudInitRenderer.render(cloudInitModel(state));
      } catch (RuntimeException error) {
        throw new OpenStackOperationException("Cloud-init preparation failed before Nova submission", error);
      }
    }, CLOUD_INIT_EXECUTOR)
        .thenCompose(userData -> createNovaServer(state, userData));
  }

  private static Map<String, Object> cloudInitModel(InstanceWorkflowState state) {
    var cloudInit = state.spec().cloudInit();
    String cidr = state.spec().network().userNetwork().ipv4CIDR();
    PreparedShare share = Objects.requireNonNull(state.preparedShare(), "preparedShare");
    PreparedNetwork network = Objects.requireNonNull(state.networkPreparation(), "networkPreparation").resources();
    if (!hasText(network.trunkId()) || !hasText(network.subPortId())
        || !hasText(network.subMacAddress()) || !hasText(network.subFixedIp())) {
      throw new IllegalStateException("Instance ports and trunk must be ready before rendering cloud-init");
    }
    return Map.ofEntries(
        Map.entry("share_access_key", share.accessKey()),
        Map.entry("share_export_location", share.exportLocationPath()),
        Map.entry("share_access_to", state.shareSpec().accessTo()),
        Map.entry("segmentId", state.spec().network().segmentationId()),
        Map.entry("macaddress", network.subMacAddress()),
        Map.entry("ipv4", network.subFixedIp() + cidr.substring(cidr.lastIndexOf('/'))),
        Map.entry("username", cloudInit.username()),
        Map.entry("password", cloudInit.password()),
        Map.entry("jupyter_token", cloudInit.jupyterToken()),
        Map.entry("hostRoutes", cloudInit.hostRoutes()));
  }

  private CompletionStage<OpenStackClient.NovaServer> createNovaServer(InstanceWorkflowState state, String userData) {
    InstanceSpec spec = state.spec();
    OpenStackClient.CreateServerRequest request =
        new OpenStackClient.CreateServerRequest(
            target(state),
            instanceId,
            state.requestId(),
            state.novaOperationId(),
            spec.name(),
            spec.imageId(),
            spec.flavorId(),
            state.hypervisorId(),
            spec.metadata(),
            state.networkPreparation().resources().parentPortId(),
            userData);
    return openStackClient.createServer(request);
  }

  private void startConfirm(InstanceWorkflowState state) {
    context.pipeToSelf(
        placement.confirm(state.hypervisorId(), state.reservationId(), instanceId),
        (result, error) -> {
          if (error != null) return new ConfirmUnknown(rootCause(error).toString());
          return switch (result.status()) {
            case SUCCEEDED -> new ConfirmSucceeded(result.idempotent());
            case REJECTED -> new ConfirmRejected(result.detail());
            case UNKNOWN -> new ConfirmUnknown(result.detail());
          };
        });
  }

  private void startCompensation(InstanceWorkflowState state) {
    CompletionStage<Boolean> delete =
        state.novaServerId() == null
            ? (state.novaOperationId() == null ? CompletableFuture.completedFuture(true)
                : openStackClient.findServerByOperationId(target(state), state.novaOperationId())
                    .thenCompose(result -> result.server() == null ? CompletableFuture.completedFuture(null)
                        : openStackClient.deleteServer(target(state), result.server().serverId()))
                    .handle((ignored, error) -> error == null))
            : openStackClient
                .deleteServer(target(state), state.novaServerId())
                .handle((ignored, error) -> error == null);
    CompletionStage<Boolean> forwardingCleanup = delete.thenCompose(deleted -> {
      if (!deleted) return CompletableFuture.completedFuture(false);
      return state.spec().forwarding() == null ? CompletableFuture.completedFuture(true)
          : floatingIpForwardings.deleteInstance(state.spec().region(), state.spec().forwarding().floatingIpIds(), instanceId)
              .handle((done, error) -> error == null && Boolean.TRUE.equals(done));
    });
    CompletionStage<Boolean> networkCleanup = forwardingCleanup.thenCompose(deleted -> {
      if (!deleted) return CompletableFuture.completedFuture(false);
      return state.networkPreparation() == null ? CompletableFuture.completedFuture(true)
          : openStackClient.deleteInstanceNetwork(target(state), networkOperationId(state))
              .handle((ignored, error) -> error == null);
    });
    CompletionStage<Boolean> compensated =
        networkCleanup.thenCompose(
            deleted -> {
              if (!deleted) return CompletableFuture.completedFuture(false);
              return state.hypervisorId() == null || state.reservationId() == null
                  ? CompletableFuture.completedFuture(true)
                  : placement
                      .cancel(state.hypervisorId(), state.reservationId())
                      .handle(
                          (result, error) ->
                              error == null
                                  && result.status()
                                      == HypervisorPlacement.ReservationResult.Status.SUCCEEDED);
            });
    context.pipeToSelf(
        compensated,
        (success, error) ->
            new CompensationCompleted(
                state.pollingGeneration(),
                error == null && Boolean.TRUE.equals(success),
                error == null ? "compensation complete" : rootCause(error).toString()));
  }

  private void scheduleProvisioningPoll(InstanceWorkflowState state) {
    timers.startSingleTimer(POLL_TIMER, new CheckProvisioning(state.pollingGeneration()), settings.pollInitialDelay());
  }

  private void scheduleConfirmRetry(InstanceWorkflowState state) {
    timers.startSingleTimer(
        CONFIRM_TIMER, new RetryConfirm(state.pollingGeneration()), settings.pollInitialDelay());
  }

  private void scheduleCompensationRetry(InstanceWorkflowState state) {
    timers.startSingleTimer(
        COMPENSATION_TIMER,
        new RetryCompensation(state.pollingGeneration()),
        settings.pollMaxDelay());
  }

  private OpenStackClient.ServerTarget target(InstanceWorkflowState state) {
    return new OpenStackClient.ServerTarget(
        state.spec().clusterId(), state.projectId(), state.spec().region());
  }

  private static String reservationId(InstanceWorkflowState state) {
    return state.reservationId() == null
        ? "res:" + state.instanceId() + ":" + state.requestId()
        : state.reservationId();
  }

  private static String operationId(InstanceWorkflowState state) {
    return state.novaOperationId() == null
        ? "nova-create:" + state.instanceId() + ":" + state.requestId()
        : state.novaOperationId();
  }

  @Override
  public EventHandler<InstanceWorkflowState, InstanceWorkflowEvent> eventHandler() {
    return newEventHandlerBuilder()
        .forAnyState()
        .onEvent(
            InstanceCreationRequested.class,
            (state, event) -> state.created(event.requestId(), event.spec()))
        .onEvent(NetworkPreparationStarted.class, (state, event) -> state.preparingNetwork(event.deadline()))
        .onEvent(NetworkPrepared.class, (state, event) -> state.networkPrepared(event.resources()))
        .onEvent(ForwardingPreparationStarted.class, (state, event) -> state.preparingForwarding(event.deadline()))
        .onEvent(ForwardingCandidateSelected.class, (state, event) -> state.forwardingCandidateSelected(event.candidateIndex()))
        .onEvent(ForwardingPrepared.class, (state, event) -> state.forwardingPrepared(event.forwarding()))
        .onEvent(SchedulingStarted.class, (state, event) -> state.scheduling(event.attempt()))
        .onEvent(
            HypervisorSelected.class,
            (state, event) ->
                state.hypervisorSelected(
                    event.hypervisorId(), event.reservationId(), event.attempts()))
        .onEvent(
            NovaCreationStarted.class,
            (state, event) ->
                state.provisioning(event.operationId(), event.startedAt(), event.deadline()))
        .onEvent(
            NovaServerCreated.class,
            (state, event) -> state.serverObserved(event.serverId(), event.status()))
        .onEvent(NovaReachedActive.class, (state, event) -> state.confirming())
        .onEvent(ReservationConfirmedForInstance.class, (state, event) -> state.touch())
        .onEvent(
            CompensationStarted.class,
            (state, event) -> state.compensating(event.failureCode(), event.failureMessage()))
        .onEvent(InstanceActivated.class, (state, event) -> state.active())
        .onEvent(
            InstanceProvisioningFailed.class,
            (state, event) -> state.failed(event.failureCode(), event.failureMessage()))
        .build();
  }

  @Override
  public SignalHandler<InstanceWorkflowState> signalHandler() {
    return newSignalHandlerBuilder()
        .onSignal(
            RecoveryCompleted.class,
            (state, signal) -> {
              networkRequestToken = null;
              novaRequestToken = null;
              forwardingRequestToken = null;
              context
                  .getLog()
                  .info(
                      "workflow recovery completed instanceId={} requestId={} phase={} "
                          + "workflowVersion={} hypervisorId={} reservationId={} novaOperationId={} novaServerId={}",
                      instanceId,
                      state.requestId(),
                      state.phase(),
                      state.version(),
                      state.hypervisorId(),
                      state.reservationId(),
                      state.novaOperationId(),
                      state.novaServerId());
              switch (state.phase()) {
                case PREPARING_NETWORK -> requestNetwork(state, false);
                case PREPARING_FORWARDING -> {
                  scheduleForwardingDeadline(state);
                  context.getSelf().tell(new CheckForwardingStatus(state.pollingGeneration(), 1));
                }
                case SCHEDULING -> startScheduling(state);
                case PROVISIONING -> requestProvisioning(state, false);
                case CONFIRMING -> scheduleConfirmRetry(state);
                case COMPENSATING -> scheduleCompensationRetry(state);
                default -> {}
              }
            })
        .build();
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    return RetentionCriteria.snapshotEvery(100, 2);
  }

  private static Throwable rootCause(Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException || current instanceof ExecutionException)
        && current.getCause() != null) current = current.getCause();
    return current;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    return value;
  }
}
