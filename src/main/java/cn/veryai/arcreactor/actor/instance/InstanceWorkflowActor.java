package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.PlacementTraits;
import cn.veryai.arcreactor.actor.instance.command.CheckNovaStatus;
import cn.veryai.arcreactor.actor.instance.command.CompensationCompleted;
import cn.veryai.arcreactor.actor.instance.command.ConfirmRejected;
import cn.veryai.arcreactor.actor.instance.command.ConfirmSucceeded;
import cn.veryai.arcreactor.actor.instance.command.ConfirmUnknown;
import cn.veryai.arcreactor.actor.instance.command.CreateInstance;
import cn.veryai.arcreactor.actor.instance.command.GetInstanceState;
import cn.veryai.arcreactor.actor.instance.command.NovaCreateFailed;
import cn.veryai.arcreactor.actor.instance.command.NovaCreateSucceeded;
import cn.veryai.arcreactor.actor.instance.command.NovaCreateUnknown;
import cn.veryai.arcreactor.actor.instance.command.NovaReconcileFound;
import cn.veryai.arcreactor.actor.instance.command.NovaReconcileNotFound;
import cn.veryai.arcreactor.actor.instance.command.NovaReconcileUnknown;
import cn.veryai.arcreactor.actor.instance.command.NovaStatusQueryFailed;
import cn.veryai.arcreactor.actor.instance.command.NovaStatusSucceeded;
import cn.veryai.arcreactor.actor.instance.command.ReconcileNova;
import cn.veryai.arcreactor.actor.instance.command.RetryCompensation;
import cn.veryai.arcreactor.actor.instance.command.RetryConfirm;
import cn.veryai.arcreactor.actor.instance.command.SchedulingFailed;
import cn.veryai.arcreactor.actor.instance.command.SchedulingSucceeded;
import cn.veryai.arcreactor.actor.instance.command.StartCompensation;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.CompensationStarted;
import cn.veryai.arcreactor.actor.instance.event.HypervisorSelected;
import cn.veryai.arcreactor.actor.instance.event.InstanceActivated;
import cn.veryai.arcreactor.actor.instance.event.InstanceCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceProvisioningFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationRetried;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationStarted;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationUnknown;
import cn.veryai.arcreactor.actor.instance.event.NovaEnteredError;
import cn.veryai.arcreactor.actor.instance.event.NovaReachedActive;
import cn.veryai.arcreactor.actor.instance.event.NovaServerCreated;
import cn.veryai.arcreactor.actor.instance.event.NovaStatusChanged;
import cn.veryai.arcreactor.actor.instance.event.ProvisioningDeadlineReached;
import cn.veryai.arcreactor.actor.instance.event.ReservationConfirmedForInstance;
import cn.veryai.arcreactor.actor.instance.event.ResourcesReservedForInstance;
import cn.veryai.arcreactor.actor.instance.event.SchedulingStarted;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import cn.veryai.arcreactor.openstack.NovaClient;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
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
  private static final String RECONCILE_TIMER = "nova-reconcile";
  private static final String CONFIRM_TIMER = "reservation-confirm";
  private static final String COMPENSATION_TIMER = "compensation";

  private final String instanceId;
  private final ActorContext<WorkflowCommand> context;
  private final TimerScheduler<WorkflowCommand> timers;
  private final HypervisorPlacement placement;
  private final OpenStackClient openStackClient;
  private final InstanceWorkflowSettings settings;
  private final Clock clock;
  private final InstanceMetrics metrics = InstanceMetrics.NOOP;

  public static Behavior<WorkflowCommand> create(
      String instanceId,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      InstanceWorkflowSettings settings) {
    return create(instanceId, placement, openStackClient, settings, Clock.systemUTC());
  }

  static Behavior<WorkflowCommand> create(
      String instanceId,
      HypervisorPlacement placement,
      OpenStackClient novaClient,
      InstanceWorkflowSettings settings,
      Clock clock) {
    return Behaviors.setup(
        context ->
            Behaviors.withTimers(
                timers ->
                    new InstanceWorkflowActor(
                        instanceId, context, timers, placement, novaClient, settings, clock)));
  }

  private InstanceWorkflowActor(
      String instanceId,
      ActorContext<WorkflowCommand> context,
      TimerScheduler<WorkflowCommand> timers,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      InstanceWorkflowSettings settings,
      Clock clock) {
    super(PersistenceId.of(ENTITY_TYPE, instanceId));
    this.instanceId = requireText(instanceId, "instanceId");
    this.context = context;
    this.timers = timers;
    this.placement = Objects.requireNonNull(placement);
    this.openStackClient = Objects.requireNonNull(openStackClient);
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
        .onCommand(SchedulingSucceeded.class, this::onSchedulingSucceeded)
        .onCommand(SchedulingFailed.class, this::onSchedulingFailed)
        .onCommand(NovaCreateSucceeded.class, this::onNovaCreateSucceeded)
        .onCommand(NovaCreateFailed.class, this::onNovaCreateFailed)
        .onCommand(NovaCreateUnknown.class, this::onNovaCreateUnknown)
        .onCommand(CheckNovaStatus.class, this::onCheckNovaStatus)
        .onCommand(NovaStatusSucceeded.class, this::onNovaStatusSucceeded)
        .onCommand(NovaStatusQueryFailed.class, this::onNovaStatusQueryFailed)
        .onCommand(ReconcileNova.class, this::onReconcileNova)
        .onCommand(NovaReconcileFound.class, this::onNovaReconcileFound)
        .onCommand(NovaReconcileNotFound.class, this::onNovaReconcileNotFound)
        .onCommand(NovaReconcileUnknown.class, this::onNovaReconcileUnknown)
        .onCommand(ConfirmSucceeded.class, this::onConfirmSucceeded)
        .onCommand(ConfirmRejected.class, this::onConfirmRejected)
        .onCommand(ConfirmUnknown.class, this::onConfirmUnknown)
        .onCommand(RetryConfirm.class, this::onRetryConfirm)
        .onCommand(StartCompensation.class, this::onStartCompensation)
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
                new SchedulingStarted(instanceId, command.requestId(), 1, now)))
        .thenRun(
            next -> {
              metrics.workflowStarted();
              command.replyTo().tell(InstanceReply.accepted(false));
              startScheduling(next);
            });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onGetState(
      InstanceWorkflowState state, GetInstanceState command) {
    command.replyTo().tell(state);
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSchedulingSucceeded(
      InstanceWorkflowState state, SchedulingSucceeded command) {
    if (state.phase() != InstancePhase.SCHEDULING) return Effect().none();
    HypervisorPlacement.ScheduleResult result = command.result();
    if (result.status() == HypervisorPlacement.ScheduleResult.Status.NO_VALID_HOST) {
      return Effect()
          .persist(
              new InstanceProvisioningFailed(
                  instanceId, "NO_VALID_HOST", result.detail(), clock.instant()))
          .thenRun(next -> metrics.workflowFailed());
    }
    String reservationId = reservationId(state);
    String operationId = operationId(state);
    Instant now = clock.instant();
    return Effect()
        .persist(
            List.of(
                new HypervisorSelected(
                    instanceId, result.hypervisorId(), reservationId, result.attempts(), now),
                new ResourcesReservedForInstance(
                    instanceId, result.hypervisorId(), reservationId, state.spec(), now),
                new NovaCreationStarted(
                    instanceId, operationId, now, now.plus(settings.provisioningTimeout()))))
        .thenRun(this::startNovaCreate);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSchedulingFailed(
      InstanceWorkflowState state, SchedulingFailed command) {
    if (state.phase() != InstancePhase.SCHEDULING) return Effect().none();
    return Effect()
        .persist(
            new InstanceProvisioningFailed(
                instanceId, "SCHEDULING_FAILED", command.reason(), clock.instant()))
        .thenRun(next -> metrics.workflowFailed());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaCreateSucceeded(
      InstanceWorkflowState state, NovaCreateSucceeded command) {
    if (state.phase() != InstancePhase.NOVA_CREATING) return Effect().none();
    return Effect()
        .persist(
            new NovaServerCreated(
                instanceId,
                state.novaOperationId(),
                command.serverId(),
                NovaClient.NovaServer.Status.BUILD,
                clock.instant()))
        .thenRun(next -> schedulePoll(next, 1));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaCreateFailed(
      InstanceWorkflowState state, NovaCreateFailed command) {
    if (state.phase() != InstancePhase.NOVA_CREATING) return Effect().none();
    return beginCompensation("NOVA_CREATE_REJECTED", command.reason());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaCreateUnknown(
      InstanceWorkflowState state, NovaCreateUnknown command) {
    if (state.phase() != InstancePhase.NOVA_CREATING
        || !Objects.equals(state.novaOperationId(), command.operationId())) return Effect().none();
    return Effect()
        .persist(
            new NovaCreationUnknown(
                instanceId, command.operationId(), command.reason(), clock.instant()))
        .thenRun(
            next -> {
              metrics.reconciliation();
              scheduleReconcileNow(next);
            });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckNovaStatus(
      InstanceWorkflowState state, CheckNovaStatus command) {
    if (state.phase() != InstancePhase.BUILDING
        || state.pollingGeneration() != command.generation()
        || !Objects.equals(state.novaServerId(), command.serverId())) return Effect().none();
    if (!clock.instant().isBefore(state.provisioningDeadline())) {
      return Effect()
          .persist(
              new ProvisioningDeadlineReached(
                  instanceId,
                  state.novaOperationId(),
                  state.provisioningDeadline(),
                  clock.instant()))
          .thenRun(
              next -> {
                metrics.reconciliation();
                scheduleReconcileNow(next);
              });
    }
    context.pipeToSelf(
        openStackClient.getServer(target(state), command.serverId()),
        (server, error) -> {
          if (error != null)
            return new NovaStatusQueryFailed(
                command.serverId(),
                command.generation(),
                command.attempt(),
                rootCause(error).toString());
          return new NovaStatusSucceeded(
              server.serverId(),
              command.generation(),
              command.attempt(),
              server.status(),
              server.fault());
        });
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaStatusSucceeded(
      InstanceWorkflowState state, NovaStatusSucceeded command) {
    if (state.phase() != InstancePhase.BUILDING
        || state.pollingGeneration() != command.generation()
        || !Objects.equals(state.novaServerId(), command.serverId())) return Effect().none();
    return switch (command.status()) {
      case ACTIVE ->
          Effect()
              .persist(new NovaReachedActive(instanceId, command.serverId(), clock.instant()))
              .thenRun(this::startConfirm);
      case ERROR ->
          Effect()
              .persist(
                  List.of(
                      new NovaEnteredError(
                          instanceId, command.serverId(), command.fault(), clock.instant()),
                      new CompensationStarted(
                          instanceId, "NOVA_ERROR", command.fault(), clock.instant())))
              .thenRun(this::startCompensation);
      case BUILD -> {
        schedulePoll(state, command.attempt() + 1);
        yield Effect().none();
      }
      case OTHER ->
          Effect()
              .persist(
                  new NovaStatusChanged(
                      instanceId, command.serverId(), command.status(), clock.instant()))
              .thenRun(next -> schedulePoll(next, command.attempt() + 1));
    };
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaStatusQueryFailed(
      InstanceWorkflowState state, NovaStatusQueryFailed command) {
    if (state.phase() == InstancePhase.BUILDING
        && state.pollingGeneration() == command.generation()
        && Objects.equals(state.novaServerId(), command.serverId())) {
      schedulePoll(state, command.attempt() + 1);
    }
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onReconcileNova(
      InstanceWorkflowState state, ReconcileNova command) {
    if (state.phase() != InstancePhase.RECONCILING
        || state.pollingGeneration() != command.generation()
        || !Objects.equals(state.novaOperationId(), command.operationId())) return Effect().none();
    context.pipeToSelf(
        openStackClient.findServerByOperationId(target(state), command.operationId()),
        (result, error) -> {
          if (error != null)
            return new NovaReconcileUnknown(command.generation(), rootCause(error).toString());
          if (result.status() == NovaClient.ServerLookupResult.Status.NOT_FOUND) {
            return new NovaReconcileNotFound(command.generation());
          }
          return new NovaReconcileFound(command.generation(), result.server());
        });
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaReconcileFound(
      InstanceWorkflowState state, NovaReconcileFound command) {
    if (state.phase() != InstancePhase.RECONCILING
        || state.pollingGeneration() != command.generation()) {
      return Effect().none();
    }
    NovaClient.NovaServer server = command.server();
    if ((server.status() == NovaClient.NovaServer.Status.BUILD
            || server.status() == NovaClient.NovaServer.Status.OTHER)
        && !clock.instant().isBefore(state.provisioningDeadline())) {
      scheduleReconcile(state);
      return Effect().none();
    }
    List<InstanceWorkflowEvent> events = new ArrayList<>();
    events.add(
        new NovaServerCreated(
            instanceId,
            state.novaOperationId(),
            server.serverId(),
            server.status(),
            clock.instant()));
    if (server.status() == NovaClient.NovaServer.Status.ACTIVE) {
      events.add(new NovaReachedActive(instanceId, server.serverId(), clock.instant()));
      return Effect().persist(events).thenRun(this::startConfirm);
    }
    if (server.status() == NovaClient.NovaServer.Status.ERROR) {
      events.add(
          new NovaEnteredError(instanceId, server.serverId(), server.fault(), clock.instant()));
      events.add(
          new CompensationStarted(instanceId, "NOVA_ERROR", server.fault(), clock.instant()));
      return Effect().persist(events).thenRun(this::startCompensation);
    }
    return Effect().persist(events).thenRun(next -> schedulePoll(next, 1));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaReconcileNotFound(
      InstanceWorkflowState state, NovaReconcileNotFound command) {
    if (state.phase() != InstancePhase.RECONCILING
        || state.pollingGeneration() != command.generation()) {
      return Effect().none();
    }
    if (!clock.instant().isBefore(state.provisioningDeadline())) {
      return beginCompensation("PROVISIONING_TIMEOUT", "Nova server was not found before deadline");
    }
    return Effect()
        .persist(new NovaCreationRetried(instanceId, state.novaOperationId(), clock.instant()))
        .thenRun(this::startNovaCreate);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaReconcileUnknown(
      InstanceWorkflowState state, NovaReconcileUnknown command) {
    if (state.phase() == InstancePhase.RECONCILING
        && state.pollingGeneration() == command.generation()) {
      scheduleReconcile(state);
    }
    return Effect().none();
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

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onStartCompensation(
      InstanceWorkflowState state, StartCompensation command) {
    if (state.phase() == InstancePhase.COMPENSATING
        && state.pollingGeneration() == command.generation()) {
      startCompensation(state);
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
              startCompensation(next);
            });
  }

  private void startScheduling(InstanceWorkflowState state) {
    InstanceSpec spec = state.spec();
    HypervisorPlacement.Resources resources =
        new HypervisorPlacement.Resources(
            spec.vcpus(), spec.memoryMb(), spec.gpuCount(), spec.requiredGpuTraits());
    var requiredTraits = new HashSet<>(spec.requiredTraits());
    requiredTraits.add(PlacementTraits.cluster(spec.clusterId()));
    if (spec.region() != null && !spec.region().isBlank()) {
      requiredTraits.add(PlacementTraits.region(spec.region()));
    }
    var request =
        new HypervisorPlacement.ScheduleRequest(
            state.requestId(),
            reservationId(state),
            instanceId,
            resources,
            settings.reservationTtl(),
            spec.availabilityZone(),
            requiredTraits);
    context.pipeToSelf(
        placement.schedule(request),
        (result, error) ->
            error == null
                ? new SchedulingSucceeded(result)
                : new SchedulingFailed(rootCause(error).toString()));
  }

  private void startNovaCreate(InstanceWorkflowState state) {
    InstanceSpec spec = state.spec();
    NovaClient.CreateServerRequest request =
        new NovaClient.CreateServerRequest(
            target(state),
            instanceId,
            state.requestId(),
            state.novaOperationId(),
            spec.name(),
            spec.imageId(),
            spec.flavorId(),
            state.hypervisorId(),
            spec.availabilityZone(),
            spec.metadata());
    context.pipeToSelf(
        openStackClient.createServer(request),
        (result, error) -> {
          if (error == null) return new NovaCreateSucceeded(result.serverId());
          Throwable cause = rootCause(error);
          return cause instanceof OpenStackOperationException
              ? new NovaCreateFailed(cause.getMessage())
              : new NovaCreateUnknown(state.novaOperationId(), cause.toString());
        });
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
            ? CompletableFuture.completedFuture(true)
            : openStackClient
                .deleteServer(target(state), state.novaServerId())
                .handle((ignored, error) -> error == null);
    CompletionStage<Boolean> compensated =
        delete.thenCompose(
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

  private void schedulePoll(InstanceWorkflowState state, int attempt) {
    CheckNovaStatus command =
        new CheckNovaStatus(state.novaServerId(), state.pollingGeneration(), attempt);
    timers.startSingleTimer(POLL_TIMER, command, settings.pollDelay(attempt));
  }

  private void scheduleReconcileNow(InstanceWorkflowState state) {
    timers.startSingleTimer(
        RECONCILE_TIMER,
        new ReconcileNova(state.novaOperationId(), state.pollingGeneration()),
        Duration.ofMillis(1));
  }

  private void scheduleReconcile(InstanceWorkflowState state) {
    timers.startSingleTimer(
        RECONCILE_TIMER,
        new ReconcileNova(state.novaOperationId(), state.pollingGeneration()),
        settings.pollMaxDelay());
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

  private NovaClient.ServerTarget target(InstanceWorkflowState state) {
    return new NovaClient.ServerTarget(
        state.spec().clusterId(), state.spec().projectId(), state.spec().region());
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
        .onEvent(SchedulingStarted.class, (state, event) -> state.scheduling(event.attempt()))
        .onEvent(
            HypervisorSelected.class,
            (state, event) ->
                state.hypervisorSelected(
                    event.hypervisorId(), event.reservationId(), event.attempts()))
        .onEvent(ResourcesReservedForInstance.class, (state, event) -> state.reserved())
        .onEvent(
            NovaCreationStarted.class,
            (state, event) ->
                state.novaCreating(event.operationId(), event.startedAt(), event.deadline()))
        .onEvent(NovaCreationRetried.class, (state, event) -> state.retryNovaCreate())
        .onEvent(
            NovaServerCreated.class,
            (state, event) -> state.building(event.serverId(), event.status()))
        .onEvent(NovaCreationUnknown.class, (state, event) -> state.reconciling())
        .onEvent(NovaStatusChanged.class, (state, event) -> state.novaStatus(event.status()))
        .onEvent(NovaReachedActive.class, (state, event) -> state.confirming())
        .onEvent(
            NovaEnteredError.class,
            (state, event) -> state.novaStatus(NovaClient.NovaServer.Status.ERROR))
        .onEvent(ProvisioningDeadlineReached.class, (state, event) -> state.reconciling())
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
                case SCHEDULING -> startScheduling(state);
                case NOVA_CREATING ->
                    context
                        .getSelf()
                        .tell(
                            new NovaCreateUnknown(
                                state.novaOperationId(), "recovery requires reconciliation"));
                case BUILDING -> schedulePoll(state, 1);
                case RECONCILING -> scheduleReconcileNow(state);
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
