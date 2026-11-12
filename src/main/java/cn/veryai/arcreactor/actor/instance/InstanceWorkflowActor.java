package cn.veryai.arcreactor.actor.instance;

import static cn.veryai.arcreactor.actor.instance.InstanceOperationIds.*;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.instance.command.CheckDeletion;
import cn.veryai.arcreactor.actor.instance.command.CheckForwardingStatus;
import cn.veryai.arcreactor.actor.instance.command.CheckProvisioning;
import cn.veryai.arcreactor.actor.instance.command.ConfirmRejected;
import cn.veryai.arcreactor.actor.instance.command.ConfirmSucceeded;
import cn.veryai.arcreactor.actor.instance.command.ConfirmUnknown;
import cn.veryai.arcreactor.actor.instance.command.CreateInstance;
import cn.veryai.arcreactor.actor.instance.command.DeleteInstance;
import cn.veryai.arcreactor.actor.instance.command.AllocationReleaseCompleted;
import cn.veryai.arcreactor.actor.instance.command.ForwardingsDeletionChecked;
import cn.veryai.arcreactor.actor.instance.command.ForwardingsDeletionSubmitted;
import cn.veryai.arcreactor.actor.instance.command.InstanceNetworkDeletionCompleted;
import cn.veryai.arcreactor.actor.instance.command.InstanceSecurityGroupDeletionCompleted;
import cn.veryai.arcreactor.actor.instance.command.ReservationCancellationCompleted;
import cn.veryai.arcreactor.actor.instance.command.ResourceReservationCompleted;
import cn.veryai.arcreactor.actor.instance.command.RetryResourceReservation;
import cn.veryai.arcreactor.actor.instance.command.DependenciesFailed;
import cn.veryai.arcreactor.actor.instance.command.DependenciesReady;
import cn.veryai.arcreactor.actor.instance.command.ForwardingStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.GetInstanceState;
import cn.veryai.arcreactor.actor.instance.command.NovaCreationCompleted;
import cn.veryai.arcreactor.actor.instance.command.NovaDeletionChecked;
import cn.veryai.arcreactor.actor.instance.command.NovaDeletionSubmitted;
import cn.veryai.arcreactor.actor.instance.command.NovaStatusChecked;
import cn.veryai.arcreactor.actor.instance.command.ParentPortPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.RecoveryStepCompleted;
import cn.veryai.arcreactor.actor.instance.command.RetryConfirm;
import cn.veryai.arcreactor.actor.instance.command.RetryRecovery;
import cn.veryai.arcreactor.actor.instance.command.SchedulingFailed;
import cn.veryai.arcreactor.actor.instance.command.SchedulingSucceeded;
import cn.veryai.arcreactor.actor.instance.command.SecurityGroupPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.SubPortPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.TrunkPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.DependenciesPrepared;
import cn.veryai.arcreactor.actor.instance.event.DependencyPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCandidateSelected;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPrepared;
import cn.veryai.arcreactor.actor.instance.event.HypervisorSelected;
import cn.veryai.arcreactor.actor.instance.event.HypervisorReservationStarted;
import cn.veryai.arcreactor.actor.instance.event.InstanceActivated;
import cn.veryai.arcreactor.actor.instance.event.InstanceCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceForwardingsDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceNetworkDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceSecurityGroupDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceReservationCancelled;
import cn.veryai.arcreactor.actor.instance.event.NovaServerDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceDeletionStarted;
import cn.veryai.arcreactor.actor.instance.event.InstanceProvisioningFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationStarted;
import cn.veryai.arcreactor.actor.instance.event.NovaDeletionAccepted;
import cn.veryai.arcreactor.actor.instance.event.NovaReachedActive;
import cn.veryai.arcreactor.actor.instance.event.NovaServerCreated;
import cn.veryai.arcreactor.actor.instance.event.ParentPortPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ParentPortPrepared;
import cn.veryai.arcreactor.actor.instance.event.ReservationConfirmedForInstance;
import cn.veryai.arcreactor.actor.instance.event.SchedulingStarted;
import cn.veryai.arcreactor.actor.instance.event.SecurityGroupPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.SecurityGroupPrepared;
import cn.veryai.arcreactor.actor.instance.event.SubPortPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.SubPortPrepared;
import cn.veryai.arcreactor.actor.instance.event.TrunkPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.TrunkPrepared;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedDependencies;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.actor.instance.model.PreparedShare;
import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import cn.veryai.arcreactor.service.InstanceDependencyPreparer;
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

/** Coordinates instance creation and externally requested deletion. */
public final class InstanceWorkflowActor
    extends EventSourcedBehavior<WorkflowCommand, InstanceWorkflowEvent, InstanceWorkflowState> {
  public static final String ENTITY_TYPE = "InstanceWorkflow";
  public static final EntityTypeKey<WorkflowCommand> TYPE_KEY =
      EntityTypeKey.create(WorkflowCommand.class, ENTITY_TYPE);

  private static final String POLL_TIMER = "nova-status-poll";
  private static final String CONFIRM_TIMER = "reservation-confirm";
  private static final String RESERVATION_TIMER = "resource-reservation";
  private static final String DELETION_TIMER = "instance-deletion";
  private static final String FORWARDING_POLL_TIMER = "forwarding-status-poll";
  private static final String RECOVERY_TIMER = "instance-recovery";
  private static final Executor CLOUD_INIT_EXECUTOR =
      task -> Thread.ofVirtual().name("instance-cloud-init").start(task);

  private final String instanceId;
  private final ActorContext<WorkflowCommand> context;
  private final TimerScheduler<WorkflowCommand> timers;
  private final HypervisorPlacement placement;
  private final OpenStackClient openStackClient;
  private final CloudInitRenderer cloudInitRenderer;
  private final FloatingIpForwardings floatingIpForwardings;
  private final InstanceDependencyPreparer dependencyPreparer;
  private final InstanceWorkflowSettings settings;
  private final Clock clock;
  private final InstanceRecovery recovery;
  private final InstanceMetrics metrics = InstanceMetrics.NOOP;
  private String dependencyRequestToken;
  private String resourceReservationRequestToken;
  private String forwardingRequestToken;
  private String parentPortRequestToken;
  private String subPortRequestToken;
  private String trunkRequestToken;
  private String securityGroupRequestToken;
  private String novaCreationRequestToken;
  private String novaStatusRequestToken;
  private String novaDeletionRequestToken;
  private String novaDeletionStatusRequestToken;
  private String forwardingDeletionRequestToken;
  private String forwardingDeletionStatusRequestToken;
  private String networkDeletionRequestToken;
  private String securityGroupDeletionRequestToken;
  private String reservationCancellationRequestToken;
  private String allocationReleaseRequestToken;
  private InstancePhase recoveringPhase;
  private String recoveryRequestToken;

  public static Behavior<WorkflowCommand> create(
      String instanceId,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceDependencyPreparer dependencyPreparer,
      InstanceWorkflowSettings settings) {
    return create(instanceId, placement, openStackClient, cloudInitRenderer,
        floatingIpForwardings, dependencyPreparer, settings, Clock.systemUTC());
  }

  static Behavior<WorkflowCommand> create(
      String instanceId,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceDependencyPreparer dependencyPreparer,
      InstanceWorkflowSettings settings,
      Clock clock) {
    return Behaviors.setup(
        context ->
            Behaviors.withTimers(
                timers ->
                    new InstanceWorkflowActor(
                        instanceId, context, timers, placement, openStackClient,
                        cloudInitRenderer, floatingIpForwardings, dependencyPreparer, settings, clock)));
  }

  private InstanceWorkflowActor(
      String instanceId,
      ActorContext<WorkflowCommand> context,
      TimerScheduler<WorkflowCommand> timers,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceDependencyPreparer dependencyPreparer,
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
    this.dependencyPreparer = Objects.requireNonNull(dependencyPreparer);
    this.settings = Objects.requireNonNull(settings);
    this.clock = Objects.requireNonNull(clock);
    this.recovery = new InstanceRecovery(placement, openStackClient, floatingIpForwardings,
        dependencyPreparer, clock);
  }

  @Override
  public InstanceWorkflowState emptyState() {
    return InstanceWorkflowState.empty(instanceId);
  }

  @Override
  public CommandHandler<WorkflowCommand, InstanceWorkflowEvent, InstanceWorkflowState>
      commandHandler() {
    var handler = newCommandHandlerBuilder()
        .forAnyState()
        .onCommand(CreateInstance.class, this::onCreate)
        .onCommand(DeleteInstance.class, this::onDelete)
        .onCommand(DependenciesReady.class, this::onDependenciesReady)
        .onCommand(DependenciesFailed.class, this::onDependenciesFailed)
        .onCommand(GetInstanceState.class, this::onGetState)
        .onCommand(SecurityGroupPreparationCompleted.class, this::onSecurityGroupPreparationCompleted)
        .onCommand(ParentPortPreparationCompleted.class, this::onParentPortPreparationCompleted)
        .onCommand(SubPortPreparationCompleted.class, this::onSubPortPreparationCompleted)
        .onCommand(TrunkPreparationCompleted.class, this::onTrunkPreparationCompleted)
        .onCommand(CheckProvisioning.class, this::onCheckProvisioning)
        .onCommand(NovaCreationCompleted.class, this::onNovaCreationCompleted)
        .onCommand(NovaStatusChecked.class, this::onNovaStatusChecked)
        .onCommand(CheckForwardingStatus.class, this::onCheckForwardingStatus)
        .onCommand(ForwardingStatusCompleted.class, this::onForwardingStatusCompleted)
        .onCommand(SchedulingSucceeded.class, this::onSchedulingSucceeded)
        .onCommand(SchedulingFailed.class, this::onSchedulingFailed)
        .onCommand(ResourceReservationCompleted.class, this::onResourceReservationCompleted)
        .onCommand(RetryResourceReservation.class, this::onRetryResourceReservation)
        .onCommand(ConfirmSucceeded.class, this::onConfirmSucceeded)
        .onCommand(ConfirmRejected.class, this::onConfirmRejected)
        .onCommand(ConfirmUnknown.class, this::onConfirmUnknown)
        .onCommand(RetryConfirm.class, this::onRetryConfirm)
        .onCommand(NovaDeletionChecked.class, this::onNovaDeletionChecked)
        .onCommand(NovaDeletionSubmitted.class, this::onNovaDeletionSubmitted)
        .onCommand(ForwardingsDeletionSubmitted.class, this::onForwardingsDeletionSubmitted)
        .onCommand(ForwardingsDeletionChecked.class, this::onForwardingsDeletionChecked)
        .onCommand(InstanceNetworkDeletionCompleted.class, this::onInstanceNetworkDeletionCompleted)
        .onCommand(InstanceSecurityGroupDeletionCompleted.class, this::onInstanceSecurityGroupDeletionCompleted)
        .onCommand(ReservationCancellationCompleted.class, this::onReservationCancellationCompleted)
        .onCommand(AllocationReleaseCompleted.class, this::onAllocationReleaseCompleted)
        .onCommand(CheckDeletion.class, this::onCheckDeletion)
        .onCommand(RetryRecovery.class, this::onRetryRecovery)
        .onCommand(RecoveryStepCompleted.class, this::onRecoveryStepCompleted)
        .build();
    return (state, command) -> {
      // Results from the previous incarnation must not enter normal creation/deletion handlers.
      if (recoveringPhase != null
          && !(command instanceof RetryRecovery || command instanceof RecoveryStepCompleted
              || command instanceof GetInstanceState
              || command instanceof CreateInstance || command instanceof DeleteInstance)) {
        return Effect().none();
      }
      return handler.apply(state, command);
    };
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
        .persist(List.of(
            new InstanceCreationRequested(instanceId, command.requestId(), command.spec(), now),
            new SchedulingStarted(instanceId, command.requestId(), now)))
        .thenRun(
            next -> {
              metrics.workflowStarted();
              command.replyTo().tell(InstanceReply.accepted(false));
              startScheduling(next);
            });
  }

  private void requestDependencies(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    dependencyRequestToken = token;
    var share = state.spec().share();
    CompletionStage<PreparedDependencies> request;
    try {
      request = dependencyPreparer.prepare(state.userId(), state.spec().clusterId(), share.shareId(),
          share.spec(), state.spec().network());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (dependencies, error) -> {
      if (error == null) return new DependenciesReady(token, dependencies);
      Throwable cause = rootCause(error);
      String code = cause instanceof BaseException business
          ? Integer.toString(business.getCode()) : "DEPENDENCY_PREPARATION_FAILED";
      return new DependenciesFailed(token, code,
          "Dependency preparation failed: " + code + " (" + cause.getClass().getSimpleName() + ")");
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onDependenciesReady(
      InstanceWorkflowState state, DependenciesReady command) {
    if (!matchesDependencies(state, command.requestToken())) return Effect().none();
    return Effect().persist(List.of(
            new DependenciesPrepared(instanceId, command.dependencies()),
            new SecurityGroupPreparationStarted(instanceId)))
        .thenRun(next -> {
          dependencyRequestToken = null;
          startSecurityGroupPreparation(next);
        });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onDependenciesFailed(
      InstanceWorkflowState state, DependenciesFailed command) {
    if (!matchesDependencies(state, command.requestToken())) return Effect().none();
    return failCreation(command.failureCode(), command.detail());
  }

  private boolean matchesDependencies(InstanceWorkflowState state, String token) {
    return state.phase() == InstancePhase.PREPARING_DEPENDENCIES
        && dependencyRequestToken != null && dependencyRequestToken.equals(token);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onGetState(
      InstanceWorkflowState state, GetInstanceState command) {
    command.replyTo().tell(state);
    return Effect().none();
  }

  private static boolean hasText(String value) { return value != null && !value.isBlank(); }

  private void startSecurityGroupPreparation(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    securityGroupRequestToken = token;
    List<Integer> ports = state.spec().forwarding() == null ? List.of() : state.spec().forwarding().internalPorts();
    CompletionStage<String> request;
    try {
      request = openStackClient.prepareInstanceSecurityGroup(target(state), instanceId,
          ports);
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (groupId, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new SecurityGroupPreparationCompleted(state.pollingGeneration(), token, groupId,
          cause == null ? null : cause instanceof OpenStackOperationException ? cause.getMessage()
              : "Security group preparation failed: " + cause.getClass().getSimpleName());
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSecurityGroupPreparationCompleted(
      InstanceWorkflowState state, SecurityGroupPreparationCompleted command) {
    if (state.phase() != InstancePhase.PREPARING_SECURITY_GROUP || state.pollingGeneration() != command.generation()
        || !Objects.equals(securityGroupRequestToken, command.requestToken())) return Effect().none();
    securityGroupRequestToken = null;
    if (command.failure() != null || !hasText(command.osSecurityGroupId())) {
      return failCreation("SECURITY_GROUP_PREPARATION_FAILED",
          command.failure() == null ? "Missing instance security group ID" : command.failure());
    }
    return Effect().persist(List.of(new SecurityGroupPrepared(instanceId, command.osSecurityGroupId()),
            new ParentPortPreparationStarted(instanceId)))
        .thenRun(next -> requestParentPort(next));
  }

  private InstanceWorkflowEvent afterNetworkStage(InstanceWorkflowState state, Instant now) {
    return state.spec().forwarding() == null
        ? novaCreationStarted(state, now)
        : new ForwardingPreparationStarted(instanceId);
  }

  private void startAfterNetwork(InstanceWorkflowState state) {
    if (state.phase() == InstancePhase.PREPARING_FORWARDING) startForwardingPreparation(state);
    else requestNovaCreation(state);
  }

  private NovaCreationStarted novaCreationStarted(InstanceWorkflowState state, Instant now) {
    return new NovaCreationStarted(instanceId, operationId(state), now);
  }

  private void requestParentPort(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    parentPortRequestToken = token;
    CompletionStage<PreparedNetwork> request;
    try {
      request = openStackClient.prepareInstanceParentPort(target(state), networkOperationId(state),
          state.dependencies().network(), state.osSecurityGroupId());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (resources, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new ParentPortPreparationCompleted(state.pollingGeneration(), token, resources,
          cause == null ? null : cause.toString());
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onParentPortPreparationCompleted(
      InstanceWorkflowState state, ParentPortPreparationCompleted command) {
    if (state.phase() != InstancePhase.PREPARING_PARENT_PORT || state.pollingGeneration() != command.generation()
        || parentPortRequestToken == null || !parentPortRequestToken.equals(command.requestToken())) return Effect().none();
    parentPortRequestToken = null;
    if (command.failure() != null || command.resources() == null) {
      return failCreation("NETWORK_PREPARATION_FAILED",
          command.failure() == null ? "Missing instance parent port preparation result" : command.failure());
    }
    return Effect().persist(List.of(new ParentPortPrepared(instanceId, command.resources()),
        new SubPortPreparationStarted(instanceId))).thenRun(this::requestSubPort);
  }

  private void requestSubPort(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    subPortRequestToken = token;
    CompletionStage<PreparedNetwork> request;
    try {
      request = openStackClient.prepareInstanceSubPort(target(state), networkOperationId(state),
          state.dependencies().network(), state.osSecurityGroupId(), state.networkPreparation().resources());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (resources, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new SubPortPreparationCompleted(state.pollingGeneration(), token, resources,
          cause == null ? null : cause.toString());
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSubPortPreparationCompleted(
      InstanceWorkflowState state, SubPortPreparationCompleted command) {
    if (state.phase() != InstancePhase.PREPARING_SUB_PORT || state.pollingGeneration() != command.generation()
        || subPortRequestToken == null || !subPortRequestToken.equals(command.requestToken())) return Effect().none();
    subPortRequestToken = null;
    if (command.failure() != null || command.resources() == null) {
      return failCreation("NETWORK_PREPARATION_FAILED",
          command.failure() == null ? "Missing instance subport preparation result" : command.failure());
    }
    return Effect().persist(List.of(new SubPortPrepared(instanceId, command.resources()),
        new TrunkPreparationStarted(instanceId))).thenRun(this::requestTrunk);
  }

  private void requestTrunk(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    trunkRequestToken = token;
    CompletionStage<PreparedNetwork> request;
    try {
      request = openStackClient.prepareInstanceTrunk(target(state), networkOperationId(state),
          state.dependencies().network(), state.networkPreparation().resources());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (resources, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new TrunkPreparationCompleted(state.pollingGeneration(), token, resources,
          cause == null ? null : cause.toString());
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onTrunkPreparationCompleted(
      InstanceWorkflowState state, TrunkPreparationCompleted command) {
    if (state.phase() != InstancePhase.PREPARING_TRUNK || state.pollingGeneration() != command.generation()
        || trunkRequestToken == null || !trunkRequestToken.equals(command.requestToken())) return Effect().none();
    trunkRequestToken = null;
    if (command.failure() != null || command.resources() == null) {
      return failCreation("NETWORK_PREPARATION_FAILED",
          command.failure() == null ? "Missing instance trunk preparation result" : command.failure());
    }
    return Effect().persist(List.of(new TrunkPrepared(instanceId, command.resources()),
        afterNetworkStage(state, clock.instant()))).thenRun(this::startAfterNetwork);
  }

  private void startForwardingPreparation(InstanceWorkflowState state) {
    requestForwarding(state, 1, true);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckForwardingStatus(
      InstanceWorkflowState state, CheckForwardingStatus command) {
    if (!matchesForwarding(state, command.generation()) || forwardingRequestToken != null) return Effect().none();
    requestForwarding(state, command.attempt(), false);
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onForwardingStatusCompleted(
      InstanceWorkflowState state, ForwardingStatusCompleted command) {
    if (!matchesForwarding(state, command.generation())
        || !Objects.equals(forwardingRequestToken, command.requestToken())) return Effect().none();
    forwardingRequestToken = null;
    ForwardingReply reply = command.reply();
    if (reply == null) {
      scheduleForwardingPoll(state, command.attempt() + 1); return Effect().none();
    }
    return switch (reply.status()) {
      case READY -> {
        ForwardingClaim claim = reply.claim();
        if (claim == null || claim.phase() != ForwardingClaim.Phase.READY
            || !forwardingClaimId(state).equals(claim.claimId()) || !forwardingSpec(state).equals(claim.spec())
            || !forwardingFloatingIpId(state).equals(claim.osFloatingIpId())
            || !hasText(claim.resources().osTcpForwardingId()) || !hasText(claim.resources().osUdpForwardingId())
            || !hasText(claim.resources().floatingIpAddress()) || claim.resources().externalPort() < 10000
            || claim.resources().externalPort() > 40000) {
          yield failCreation("FORWARDING_RESULT_INVALID", "Incomplete or mismatched port forwarding result");
        }
        Instant now = clock.instant();
        List<InstanceWorkflowEvent> events = new ArrayList<>();
        events.add(new ForwardingPrepared(instanceId, PreparedForwarding.from(claim), now));
        boolean complete = state.preparedForwardings().size() + 1 == state.spec().forwarding().internalPorts().size();
        if (complete) events.add(novaCreationStarted(state, now));
        yield Effect().persist(events).thenRun(next -> {
          if (complete) {
            cancelForwardingTimers();
            requestNovaCreation(next);
          } else {
            startForwardingPreparation(next);
          }
        });
      }
      case FAILED, DELETE_FAILED, REJECTED, DELETED -> {
        if (reply.claim() != null && reply.claim().ownsPort()) {
          // A failed create may have left rules behind even without confirmed resource IDs.
          yield failCreation("FORWARDING_PREPARATION_FAILED", reply.detail());
        }
        int nextCandidate = state.forwardingCandidateIndex() + 1;
        if (nextCandidate >= state.spec().forwarding().osFloatingIpIds().size()) {
          yield failCreation("FORWARDING_PREPARATION_FAILED",
              "All floating IP candidates failed for internal port " + state.currentForwardingPort() + ": " + reply.detail());
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
      case IN_PROGRESS -> {
        scheduleForwardingPoll(state, command.attempt() + 1); yield Effect().none();
      }
    };
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
    return new ForwardingSpec(instanceId, state.userId(), state.spec().region(),
        state.osProjectId(), network.osParentPortId(), network.parentFixedIp(), state.currentForwardingPort());
  }
  private static String forwardingFloatingIpId(InstanceWorkflowState state) {
    return state.spec().forwarding().osFloatingIpIds().get(state.forwardingCandidateIndex());
  }
  private static boolean matchesForwarding(InstanceWorkflowState state, long generation) {
    return state.phase() == InstancePhase.PREPARING_FORWARDING && state.pollingGeneration() == generation;
  }
  private void scheduleForwardingPoll(InstanceWorkflowState state, int attempt) {
    timers.startSingleTimer(FORWARDING_POLL_TIMER,
        new CheckForwardingStatus(state.pollingGeneration(), attempt), settings.pollDelay(attempt));
  }
  private void cancelForwardingTimers() {
    timers.cancel(FORWARDING_POLL_TIMER);
    forwardingRequestToken = null;
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSchedulingSucceeded(
      InstanceWorkflowState state, SchedulingSucceeded command) {
    if (state.phase() != InstancePhase.SCHEDULING) return Effect().none();
    HypervisorPlacement.ScheduleResult result = command.result();
    if (result.hypervisorIds().isEmpty()) {
      return failCreation("NO_VALID_HOST", result.detail());
    }
    return Effect().persist(reservationStarted(state, result.hypervisorIds()))
        .thenRun(this::requestResourceReservation);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSchedulingFailed(
      InstanceWorkflowState state, SchedulingFailed command) {
    if (state.phase() != InstancePhase.SCHEDULING) return Effect().none();
    return failCreation("SCHEDULING_FAILED", command.reason());
  }

  private HypervisorReservationStarted reservationStarted(InstanceWorkflowState state, List<String> candidates) {
    return new HypervisorReservationStarted(instanceId, candidates.getFirst(), reservationId(state),
        candidates.subList(1, candidates.size()), clock.instant());
  }

  private void requestResourceReservation(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.RESERVING_RESOURCES || resourceReservationRequestToken != null) return;
    String token = UUID.randomUUID().toString();
    resourceReservationRequestToken = token;
    CompletionStage<HypervisorPlacement.ReserveResult> request;
    try {
      // The target host and reservation ID are already durable before this side effect starts.
      request = placement.reserve(state.hypervisorId(), schedulingRequest(state));
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (result, error) -> new ResourceReservationCompleted(
        state.pollingGeneration(), token, error == null && result != null ? result
            : new HypervisorPlacement.ReserveResult(HypervisorPlacement.ReserveResult.Status.UNKNOWN,
                error == null ? "Missing reservation result" : rootCause(error).toString())));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onResourceReservationCompleted(
      InstanceWorkflowState state, ResourceReservationCompleted command) {
    if (state.phase() != InstancePhase.RESERVING_RESOURCES || state.pollingGeneration() != command.generation()
        || resourceReservationRequestToken == null
        || !resourceReservationRequestToken.equals(command.requestToken())) return Effect().none();
    resourceReservationRequestToken = null;
    return switch (command.result().status()) {
      case ACCEPTED -> Effect().persist(List.of(
              new HypervisorSelected(instanceId, state.hypervisorId(), state.reservationId(), clock.instant()),
              new DependencyPreparationStarted(instanceId)))
          .thenRun(next -> {
            timers.cancel(RESERVATION_TIMER);
            requestDependencies(next);
          });
      case UNAVAILABLE -> {
        if (state.remainingHypervisorIds().isEmpty()) {
          yield failCreation("NO_VALID_HOST", command.result().detail());
        }
        yield Effect().persist(reservationStarted(state, state.remainingHypervisorIds()))
            .thenRun(next -> {
              timers.cancel(RESERVATION_TIMER);
              requestResourceReservation(next);
            });
      }
      case REJECTED -> failCreation("RESERVATION_REJECTED", command.result().detail());
      case UNKNOWN -> {
        context.getLog().warn("Resource reservation outcome unknown: instanceId={} hypervisorId={} reservationId={} reason={}",
            instanceId, state.hypervisorId(), state.reservationId(), command.result().detail());
        timers.startSingleTimer(RESERVATION_TIMER, new RetryResourceReservation(state.pollingGeneration()),
            settings.pollInitialDelay());
        yield Effect().none();
      }
    };
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onRetryResourceReservation(
      InstanceWorkflowState state, RetryResourceReservation command) {
    if (state.phase() == InstancePhase.RESERVING_RESOURCES && state.pollingGeneration() == command.generation()) {
      requestResourceReservation(state);
    }
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckProvisioning(
      InstanceWorkflowState state, CheckProvisioning command) {
    if (state.phase() != InstancePhase.PROVISIONING || state.pollingGeneration() != command.generation()) return Effect().none();
    if (novaCreationRequestToken == null && novaStatusRequestToken == null) requestNovaStatus(state);
    else scheduleProvisioningPoll(state);
    return Effect().none();
  }

  private void requestNovaCreation(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    novaCreationRequestToken = token;
    CompletionStage<OpenStackClient.NovaServer> request;
    try {
      request = submitNova(state);
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (server, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new NovaCreationCompleted(state.pollingGeneration(), token, server,
          cause instanceof OpenStackOperationException || cause instanceof CloudInitRenderer.RenderingException,
          cause == null ? null : cause.toString());
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaCreationCompleted(
      InstanceWorkflowState state, NovaCreationCompleted command) {
    if (state.phase() != InstancePhase.PROVISIONING || state.pollingGeneration() != command.generation()
        || novaCreationRequestToken == null || !novaCreationRequestToken.equals(command.requestToken())) return Effect().none();
    novaCreationRequestToken = null;
    if (command.rejected()) return failCreation("NOVA_CREATE_REJECTED", command.failure());
    if (command.failure() != null || command.server() == null) {
      return failCreation("NOVA_CREATE_FAILED",
          command.failure() == null ? "Nova returned no server" : command.failure());
    }
    OpenStackClient.NovaServer server = command.server();
    Instant now = clock.instant();
    List<InstanceWorkflowEvent> events = new ArrayList<>();
    events.add(new NovaServerCreated(instanceId, state.novaOperationId(), server.osServerId(), server.status(), now));
    if (server.status() == OpenStackClient.NovaServer.Status.ACTIVE) {
      events.add(new NovaReachedActive(instanceId, server.osServerId(), now));
      return Effect().persist(events).thenRun(this::startConfirm);
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ERROR) {
      events.add(new InstanceProvisioningFailed(instanceId, "NOVA_ERROR", server.fault(), now));
      return Effect().persist(events).thenRun(next -> creationFailed());
    }
    return Effect().persist(events).thenRun(this::scheduleProvisioningPoll);
  }

  private void requestNovaStatus(InstanceWorkflowState state) {
    if (state.osNovaServerId() == null) {
      startNovaRecovery(state);
      return;
    }
    String token = UUID.randomUUID().toString();
    novaStatusRequestToken = token;
    CompletionStage<OpenStackClient.NovaServer> request;
    try {
      request = openStackClient.getServer(target(state), state.osNovaServerId());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (server, error) -> new NovaStatusChecked(
        state.pollingGeneration(), token, server, error == null ? null : rootCause(error).toString()));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaStatusChecked(
      InstanceWorkflowState state, NovaStatusChecked command) {
    if (state.phase() != InstancePhase.PROVISIONING || state.pollingGeneration() != command.generation()
        || novaStatusRequestToken == null || !novaStatusRequestToken.equals(command.requestToken())) return Effect().none();
    novaStatusRequestToken = null;
    if (command.failure() != null) {
      scheduleProvisioningPoll(state);
      return Effect().none();
    }
    if (command.server() == null) {
      return failCreation("NOVA_SERVER_MISSING", "The recorded Nova server no longer exists");
    }
    OpenStackClient.NovaServer server = command.server();
    List<InstanceWorkflowEvent> events = new ArrayList<>();
    Instant now = clock.instant();
    if (!server.osServerId().equals(state.osNovaServerId()) || server.status() != state.novaStatus()) {
      events.add(new NovaServerCreated(instanceId, state.novaOperationId(), server.osServerId(), server.status(), now));
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ACTIVE) {
      events.add(new NovaReachedActive(instanceId, server.osServerId(), now));
      return Effect().persist(events).thenRun(this::startConfirm);
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ERROR) {
      events.add(new InstanceProvisioningFailed(instanceId, "NOVA_ERROR", server.fault(), now));
      return Effect().persist(events).thenRun(next -> creationFailed());
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
                new InstanceActivated(instanceId, state.osNovaServerId(), state.hypervisorId(), now)))
        .thenRun(next -> metrics.workflowActive());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onConfirmRejected(
      InstanceWorkflowState state, ConfirmRejected command) {
    if (state.phase() != InstancePhase.CONFIRMING) return Effect().none();
    return failCreation("RESERVATION_CONFIRM_REJECTED", command.reason());
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

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onDelete(
      InstanceWorkflowState state, DeleteInstance command) {
    if (state.phase().isDeleting() || state.phase() == InstancePhase.DELETED) {
      command.replyTo().tell(InstanceReply.accepted(true));
      return Effect().none();
    }
    if (state.phase() != InstancePhase.FAILED) {
      command.replyTo().tell(InstanceReply.rejected("Only failed instances can be deleted by this operation"));
      return Effect().none();
    }
    return Effect().persist(new InstanceDeletionStarted(instanceId, clock.instant()))
        .thenRun(next -> {
          command.replyTo().tell(InstanceReply.accepted(false));
          startDeletionStage(next);
        });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaDeletionChecked(
      InstanceWorkflowState state, NovaDeletionChecked command) {
    if (!matchesDeletion(state, InstancePhase.DELETING_NOVA, command.generation(),
        novaDeletionStatusRequestToken, command.requestToken())) return Effect().none();
    novaDeletionStatusRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    if (command.osServerId() == null) {
      return Effect().persist(new NovaServerDeleted(instanceId, clock.instant()))
          .thenRun(this::startDeletionStage);
    }
    if (!state.novaDeletionAccepted()) requestNovaDeletion(state, command.osServerId());
    else scheduleDeletionCheck(state);
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNovaDeletionSubmitted(
      InstanceWorkflowState state, NovaDeletionSubmitted command) {
    if (!matchesDeletion(state, InstancePhase.DELETING_NOVA, command.generation(),
        novaDeletionRequestToken, command.requestToken())) return Effect().none();
    novaDeletionRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    return Effect().persist(new NovaDeletionAccepted(instanceId, command.osServerId(), clock.instant()))
        .thenRun(this::scheduleDeletionCheck);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onForwardingsDeletionSubmitted(
      InstanceWorkflowState state, ForwardingsDeletionSubmitted command) {
    if (!matchesDeletion(state, InstancePhase.DELETING_FORWARDINGS, command.generation(),
        forwardingDeletionRequestToken, command.requestToken())) return Effect().none();
    forwardingDeletionRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    if (command.deleted()) {
      return Effect().persist(new InstanceForwardingsDeleted(instanceId, clock.instant()))
          .thenRun(this::startDeletionStage);
    }
    // A delete reply may only acknowledge acceptance; the next request queries the persisted claim state.
    scheduleDeletionCheck(state);
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onForwardingsDeletionChecked(
      InstanceWorkflowState state, ForwardingsDeletionChecked command) {
    if (!matchesDeletion(state, InstancePhase.DELETING_FORWARDINGS, command.generation(),
        forwardingDeletionStatusRequestToken, command.requestToken())) return Effect().none();
    forwardingDeletionStatusRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    boolean deleted = true;
    boolean retry = false;
    for (ForwardingsDeletionChecked.Result result : command.results()) {
      if (result.failure() != null || result.reply() == null) {
        deleted = false;
        continue;
      }
      if (result.reply().status() == ForwardingReply.Status.DELETED) continue;
      deleted = false;
      switch (result.reply().status()) {
        case READY, FAILED, DELETE_FAILED, NOT_FOUND -> retry = true;
        default -> { }
      }
    }
    if (deleted) {
      return Effect().persist(new InstanceForwardingsDeleted(instanceId, clock.instant()))
          .thenRun(this::startDeletionStage);
    }
    if (retry) requestForwardingsDeletion(state);
    else scheduleDeletionCheck(state);
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onInstanceNetworkDeletionCompleted(
      InstanceWorkflowState state, InstanceNetworkDeletionCompleted command) {
    if (!matchesDeletion(state, InstancePhase.DELETING_NETWORK, command.generation(),
        networkDeletionRequestToken, command.requestToken())) return Effect().none();
    networkDeletionRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    return Effect().persist(new InstanceNetworkDeleted(instanceId, clock.instant()))
        .thenRun(this::startDeletionStage);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onInstanceSecurityGroupDeletionCompleted(
      InstanceWorkflowState state, InstanceSecurityGroupDeletionCompleted command) {
    if (!matchesDeletion(state, InstancePhase.DELETING_SECURITY_GROUP, command.generation(),
        securityGroupDeletionRequestToken, command.requestToken())) return Effect().none();
    securityGroupDeletionRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    return Effect().persist(new InstanceSecurityGroupDeleted(instanceId, clock.instant()))
        .thenRun(this::startDeletionStage);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onReservationCancellationCompleted(
      InstanceWorkflowState state, ReservationCancellationCompleted command) {
    if (!matchesDeletion(state, InstancePhase.CANCELLING_RESERVATION, command.generation(),
        reservationCancellationRequestToken, command.requestToken())) return Effect().none();
    reservationCancellationRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    if (command.result() == null || command.result().status() != HypervisorPlacement.ReservationResult.Status.SUCCEEDED) {
      return retryDeletionStage(state, command.result() == null ? "Missing cancellation result" : command.result().detail());
    }
    return Effect().persist(new InstanceReservationCancelled(instanceId, clock.instant()))
        .thenRun(this::startDeletionStage);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onAllocationReleaseCompleted(
      InstanceWorkflowState state, AllocationReleaseCompleted command) {
    if (!matchesDeletion(state, InstancePhase.RELEASING_ALLOCATION, command.generation(),
        allocationReleaseRequestToken, command.requestToken())) return Effect().none();
    allocationReleaseRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    if (command.result() == null || command.result().status() != HypervisorPlacement.ReservationResult.Status.SUCCEEDED) {
      return retryDeletionStage(state, command.result() == null ? "Missing release result" : command.result().detail());
    }
    return Effect().persist(new InstanceDeleted(instanceId, clock.instant()))
        .thenRun(next -> timers.cancel(DELETION_TIMER));
  }

  private boolean matchesDeletion(InstanceWorkflowState state, InstancePhase phase, long generation,
      String expectedToken, String token) {
    return state.phase() == phase && state.pollingGeneration() == generation
        && expectedToken != null && expectedToken.equals(token);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> retryDeletionStage(
      InstanceWorkflowState state, String reason) {
    context.getLog().warn("Instance deletion incomplete: instanceId={} phase={} reason={}", instanceId, state.phase(), reason);
    scheduleDeletionCheck(state);
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckDeletion(
      InstanceWorkflowState state, CheckDeletion command) {
    if (!state.phase().isDeleting() || state.pollingGeneration() != command.generation()) return Effect().none();
    switch (state.phase()) {
      case DELETING_NOVA -> requestNovaDeletionStatus(state);
      case DELETING_FORWARDINGS -> requestForwardingsDeletionStatus(state);
      default -> startDeletionStage(state);
    }
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> failCreation(
      String code, String message) {
    return Effect().persist(new InstanceProvisioningFailed(instanceId, code, message, clock.instant()))
        .thenRun(next -> creationFailed());
  }

  private void creationFailed() {
    timers.cancelAll();
    recoveringPhase = null;
    recoveryRequestToken = null;
    dependencyRequestToken = null;
    resourceReservationRequestToken = null;
    securityGroupRequestToken = null;
    parentPortRequestToken = null;
    subPortRequestToken = null;
    trunkRequestToken = null;
    novaCreationRequestToken = null;
    novaStatusRequestToken = null;
    forwardingRequestToken = null;
    metrics.workflowFailed();
  }

  private void startScheduling(InstanceWorkflowState state) {
    CompletionStage<HypervisorPlacement.ScheduleResult> scheduling;
    try {
      scheduling = placement.schedule(schedulingRequest(state));
    } catch (RuntimeException error) {
      scheduling = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(
        scheduling,
        (result, error) ->
            error == null
                ? new SchedulingSucceeded(result)
                : new SchedulingFailed(rootCause(error).toString()));
  }

  private HypervisorPlacement.ScheduleRequest schedulingRequest(InstanceWorkflowState state) {
    InstanceSpec spec = state.spec();
    return new HypervisorPlacement.ScheduleRequest(state.requestId(), reservationId(state), instanceId,
        new HypervisorPlacement.Resources(spec.vcpus(), spec.memoryGb(), spec.diskGb(), spec.gpuCount()),
        null, spec.region(), spec.hypervisorType(), spec.hostname());
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
    if (!hasText(network.osTrunkId()) || !hasText(network.osSubPortId())
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
            spec.osImageId(),
            spec.osFlavorId(),
            state.hypervisorId(),
            spec.metadata(),
            state.networkPreparation().resources().osParentPortId(),
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

  private void startDeletionStage(InstanceWorkflowState state) {
    switch (state.phase()) {
      case DELETING_NOVA -> {
        if (state.osNovaServerId() != null && !state.novaDeletionAccepted()) {
          requestNovaDeletion(state, state.osNovaServerId());
        } else {
          requestNovaDeletionStatus(state);
        }
      }
      case DELETING_FORWARDINGS -> requestForwardingsDeletion(state);
      case DELETING_NETWORK -> requestInstanceNetworkDeletion(state);
      case DELETING_SECURITY_GROUP -> requestInstanceSecurityGroupDeletion(state);
      case CANCELLING_RESERVATION -> requestReservationCancellation(state);
      case RELEASING_ALLOCATION -> requestAllocationRelease(state);
      default -> throw new IllegalStateException("Cannot start deletion stage: " + state.phase());
    }
  }

  private boolean deletionInFlight() {
    return novaDeletionRequestToken != null || novaDeletionStatusRequestToken != null
        || forwardingDeletionRequestToken != null || forwardingDeletionStatusRequestToken != null
        || networkDeletionRequestToken != null || securityGroupDeletionRequestToken != null
        || reservationCancellationRequestToken != null || allocationReleaseRequestToken != null;
  }

  private void requestNovaDeletionStatus(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.DELETING_NOVA || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    novaDeletionStatusRequestToken = token;
    CompletionStage<OpenStackClient.NovaServer> request;
    try {
      if (state.osNovaServerId() != null) {
        request = openStackClient.getServer(target(state), state.osNovaServerId());
      } else if (state.novaOperationId() != null) {
        request = openStackClient.findServerByOperationId(target(state), state.novaOperationId())
            .thenApply(OpenStackClient.ServerLookupResult::server);
      } else {
        request = CompletableFuture.completedFuture(null);
      }
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (server, error) -> new NovaDeletionChecked(
        state.pollingGeneration(), token, server == null ? null : server.osServerId(),
        error == null ? null : rootCause(error).toString()));
  }

  private void requestNovaDeletion(InstanceWorkflowState state, String osServerId) {
    if (state.phase() != InstancePhase.DELETING_NOVA || state.novaDeletionAccepted() || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    novaDeletionRequestToken = token;
    CompletionStage<Void> request;
    try {
      request = openStackClient.deleteServer(target(state), osServerId);
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (ignored, error) -> new NovaDeletionSubmitted(
        state.pollingGeneration(), token, osServerId, error == null ? null : rootCause(error).toString()));
  }

  private void requestForwardingsDeletion(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.DELETING_FORWARDINGS || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    forwardingDeletionRequestToken = token;
    CompletionStage<Boolean> request;
    try {
      List<CompletableFuture<ForwardingReply>> deletions = new ArrayList<>();
      for (PreparedForwarding forwarding : state.preparedForwardings()) {
        deletions.add(floatingIpForwardings.delete(state.spec().region(), forwarding.osFloatingIpId(), forwarding.claimId())
            .toCompletableFuture());
      }
      request = CompletableFuture.allOf(deletions.toArray(CompletableFuture[]::new))
          .thenApply(ignored -> deletions.stream().map(CompletableFuture::join)
              .allMatch(reply -> reply != null && reply.status() == ForwardingReply.Status.DELETED));
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (deleted, error) -> new ForwardingsDeletionSubmitted(
        state.pollingGeneration(), token, Boolean.TRUE.equals(deleted), error == null ? null : rootCause(error).toString()));
  }

  private void requestForwardingsDeletionStatus(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.DELETING_FORWARDINGS || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    forwardingDeletionStatusRequestToken = token;
    CompletionStage<List<ForwardingsDeletionChecked.Result>> request;
    try {
      List<CompletableFuture<ForwardingsDeletionChecked.Result>> queries = new ArrayList<>();
      for (PreparedForwarding forwarding : state.preparedForwardings()) {
        queries.add(floatingIpForwardings.get(state.spec().region(), forwarding.osFloatingIpId(), forwarding.claimId())
            .handle((reply, error) -> new ForwardingsDeletionChecked.Result(forwarding, reply,
                error == null ? null : rootCause(error).toString())).toCompletableFuture());
      }
      request = CompletableFuture.allOf(queries.toArray(CompletableFuture[]::new))
          .thenApply(ignored -> queries.stream().map(CompletableFuture::join).toList());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (results, error) -> new ForwardingsDeletionChecked(
        state.pollingGeneration(), token, results == null ? List.of() : results,
        error == null ? null : rootCause(error).toString()));
  }

  private void requestInstanceNetworkDeletion(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.DELETING_NETWORK || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    networkDeletionRequestToken = token;
    CompletionStage<Void> request;
    try {
      request = state.networkPreparation() == null ? CompletableFuture.completedFuture(null)
          : openStackClient.deleteInstanceNetwork(target(state), networkOperationId(state));
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (ignored, error) -> new InstanceNetworkDeletionCompleted(
        state.pollingGeneration(), token, error == null ? null : rootCause(error).toString()));
  }

  private void requestInstanceSecurityGroupDeletion(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.DELETING_SECURITY_GROUP || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    securityGroupDeletionRequestToken = token;
    CompletionStage<Void> request;
    try {
      request = state.securityGroupPreparation() == null ? CompletableFuture.completedFuture(null)
          : openStackClient.deleteInstanceSecurityGroup(target(state), instanceId);
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (ignored, error) -> new InstanceSecurityGroupDeletionCompleted(
        state.pollingGeneration(), token, error == null ? null : rootCause(error).toString()));
  }

  private void requestReservationCancellation(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.CANCELLING_RESERVATION || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    reservationCancellationRequestToken = token;
    CompletionStage<HypervisorPlacement.ReservationResult> request;
    try {
      request = state.hypervisorId() == null || state.reservationId() == null
          ? CompletableFuture.completedFuture(HypervisorPlacement.ReservationResult.succeeded(true, "no reservation"))
          : placement.cancel(state.hypervisorId(), state.reservationId());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (result, error) -> new ReservationCancellationCompleted(
        state.pollingGeneration(), token, result, error == null ? null : rootCause(error).toString()));
  }

  private void requestAllocationRelease(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.RELEASING_ALLOCATION || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    allocationReleaseRequestToken = token;
    CompletionStage<HypervisorPlacement.ReservationResult> request;
    try {
      request = state.hypervisorId() == null
          ? CompletableFuture.completedFuture(HypervisorPlacement.ReservationResult.succeeded(true, "no allocation"))
          : placement.release(state.hypervisorId(), instanceId);
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (result, error) -> new AllocationReleaseCompleted(
        state.pollingGeneration(), token, result, error == null ? null : rootCause(error).toString()));
  }

  private void scheduleProvisioningPoll(InstanceWorkflowState state) {
    timers.startSingleTimer(POLL_TIMER, new CheckProvisioning(state.pollingGeneration()), settings.pollInitialDelay());
  }

  private void scheduleConfirmRetry(InstanceWorkflowState state) {
    timers.startSingleTimer(
        CONFIRM_TIMER, new RetryConfirm(state.pollingGeneration()), settings.pollInitialDelay());
  }

  private void scheduleDeletionCheck(InstanceWorkflowState state) {
    timers.startSingleTimer(
        DELETION_TIMER,
        new CheckDeletion(state.pollingGeneration()),
        settings.pollMaxDelay());
  }

  private OpenStackClient.ServerTarget target(InstanceWorkflowState state) {
    return new OpenStackClient.ServerTarget(
        state.spec().clusterId(), state.osProjectId(), state.spec().region());
  }

  @Override
  public EventHandler<InstanceWorkflowState, InstanceWorkflowEvent> eventHandler() {
    return newEventHandlerBuilder()
        .forAnyState()
        .onEvent(
            InstanceCreationRequested.class,
            (state, event) -> state.created(event.requestId(), event.spec()))
        .onEvent(DependencyPreparationStarted.class, (state, event) -> state.preparingDependencies())
        .onEvent(DependenciesPrepared.class, (state, event) -> state.dependenciesPrepared(event.dependencies()))
        .onEvent(SecurityGroupPreparationStarted.class, (state, event) -> state.preparingSecurityGroup())
        .onEvent(SecurityGroupPrepared.class, (state, event) -> state.securityGroupPrepared(event.osSecurityGroupId()))
        .onEvent(ParentPortPreparationStarted.class, (state, event) -> state.preparingParentPort())
        .onEvent(ParentPortPrepared.class, (state, event) -> state.networkPrepared(event.resources()))
        .onEvent(SubPortPreparationStarted.class, (state, event) -> state.preparingSubPort())
        .onEvent(SubPortPrepared.class, (state, event) -> state.networkPrepared(event.resources()))
        .onEvent(TrunkPreparationStarted.class, (state, event) -> state.preparingTrunk())
        .onEvent(TrunkPrepared.class, (state, event) -> state.networkPrepared(event.resources()))
        .onEvent(ForwardingPreparationStarted.class, (state, event) -> state.preparingForwarding())
        .onEvent(ForwardingCandidateSelected.class, (state, event) -> state.forwardingCandidateSelected(event.candidateIndex()))
        .onEvent(ForwardingPrepared.class, (state, event) -> state.forwardingPrepared(event.forwarding()))
        .onEvent(SchedulingStarted.class, (state, event) -> state.scheduling())
        .onEvent(HypervisorReservationStarted.class, (state, event) -> state.reservingResources(
            event.hypervisorId(), event.reservationId(), event.remainingHypervisorIds()))
        .onEvent(
            HypervisorSelected.class,
            (state, event) ->
                state.hypervisorSelected(
                    event.hypervisorId(), event.reservationId()))
        .onEvent(
            NovaCreationStarted.class,
            (state, event) ->
                state.provisioning(event.operationId(), event.startedAt()))
        .onEvent(
            NovaServerCreated.class,
            (state, event) -> state.serverObserved(event.osServerId(), event.status()))
        .onEvent(NovaReachedActive.class, (state, event) -> state.confirming())
        .onEvent(ReservationConfirmedForInstance.class, (state, event) -> state.touch())
        .onEvent(
            InstanceDeletionStarted.class,
            (state, event) -> state.deleting())
        .onEvent(NovaDeletionAccepted.class, (state, event) -> state.novaDeletionAccepted(event.osServerId()))
        .onEvent(NovaServerDeleted.class, (state, event) -> state.novaDeleted())
        .onEvent(InstanceForwardingsDeleted.class, (state, event) -> state.forwardingsDeleted())
        .onEvent(InstanceNetworkDeleted.class, (state, event) -> state.networkDeleted())
        .onEvent(InstanceSecurityGroupDeleted.class, (state, event) -> state.securityGroupDeleted())
        .onEvent(InstanceReservationCancelled.class, (state, event) -> state.reservationCancelled())
        .onEvent(InstanceDeleted.class, (state, event) -> state.deleted())
        .onEvent(InstanceActivated.class, (state, event) -> state.active())
        .onEvent(
            InstanceProvisioningFailed.class,
            (state, event) -> state.failed(event.failureCode(), event.failureMessage()))
        .build();
  }

  @Override
  public SignalHandler<InstanceWorkflowState> signalHandler() {
    return newSignalHandlerBuilder()
        .onSignal(RecoveryCompleted.class, (state, signal) -> recoverState(state))
        .build();
  }

  private void startNovaRecovery(InstanceWorkflowState state) {
    timers.cancel(POLL_TIMER);
    novaCreationRequestToken = null;
    novaStatusRequestToken = null;
    recoveringPhase = InstancePhase.PROVISIONING;
    scheduleRecovery(state, 1);
  }

  private void recoverState(InstanceWorkflowState state) {
    timers.cancelAll();
    dependencyRequestToken = null;
    resourceReservationRequestToken = null;
    securityGroupRequestToken = null;
    parentPortRequestToken = null;
    subPortRequestToken = null;
    trunkRequestToken = null;
    novaCreationRequestToken = null;
    novaStatusRequestToken = null;
    forwardingRequestToken = null;
    novaDeletionRequestToken = null;
    novaDeletionStatusRequestToken = null;
    forwardingDeletionRequestToken = null;
    forwardingDeletionStatusRequestToken = null;
    networkDeletionRequestToken = null;
    securityGroupDeletionRequestToken = null;
    reservationCancellationRequestToken = null;
    allocationReleaseRequestToken = null;
    recoveryRequestToken = null;
    context.getLog().info(
        "workflow recovery completed instanceId={} requestId={} phase={} "
            + "workflowVersion={} hypervisorId={} reservationId={} novaOperationId={} osNovaServerId={}",
        instanceId, state.requestId(), state.phase(), state.version(), state.hypervisorId(),
        state.reservationId(), state.novaOperationId(), state.osNovaServerId());
    recoveringPhase = state.phase().isDeleting() ? state.phase() : switch (state.phase()) {
      case SCHEDULING, RESERVING_RESOURCES, PREPARING_DEPENDENCIES, PREPARING_SECURITY_GROUP,
          PREPARING_PARENT_PORT, PREPARING_SUB_PORT, PREPARING_TRUNK,
          PREPARING_FORWARDING, PROVISIONING, CONFIRMING -> state.phase();
      default -> null;
    };
    if (recoveringPhase == null) return;
    if (recoveringPhase == InstancePhase.CONFIRMING || recoveringPhase.isDeleting()) {
      scheduleRecovery(state, 1);
    } else {
      requestRecovery(state, 1);
    }
  }

  private void requestRecovery(InstanceWorkflowState state, int attempt) {
    String token = UUID.randomUUID().toString();
    recoveryRequestToken = token;
    CompletionStage<InstanceRecovery.Outcome> request = recovery.recover(state);
    context.pipeToSelf(request, (outcome, error) -> new RecoveryStepCompleted(
        state.phase(), state.pollingGeneration(), token, attempt, outcome,
        error == null ? null : rootCause(error).toString()));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onRetryRecovery(
      InstanceWorkflowState state, RetryRecovery command) {
    if (matchesRecovery(state, command.phase(), command.generation()) && recoveryRequestToken == null) {
      requestRecovery(state, command.attempt());
    }
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onRecoveryStepCompleted(
      InstanceWorkflowState state, RecoveryStepCompleted command) {
    if (!matchesRecovery(state, command.phase(), command.generation())
        || recoveryRequestToken == null || !recoveryRequestToken.equals(command.requestToken())) return Effect().none();
    recoveryRequestToken = null;
    if (command.failure() != null || command.outcome() == null) {
      context.getLog().warn("Instance recovery incomplete: instanceId={} phase={} reason={}",
          instanceId, state.phase(), command.failure());
      scheduleRecovery(state, command.attempt() + 1);
      return Effect().none();
    }
    InstanceRecovery.Outcome outcome = command.outcome();
    var effect = outcome.events().isEmpty() ? Effect().none() : Effect().persist(outcome.events());
    return effect.thenRun(next -> {
      switch (outcome.action()) {
        case RETRY -> scheduleRecovery(next, command.attempt() + 1);
        case CONTINUE_CREATION -> {
          finishRecovery();
          continueCreationAfterRecovery(next);
        }
        case CONTINUE_DELETION -> {
          recoveringPhase = next.phase();
          requestRecovery(next, 1);
        }
        case STOP -> {
          finishRecovery();
          if (next.phase() == InstancePhase.FAILED) creationFailed();
          else if (next.phase() == InstancePhase.ACTIVE) metrics.workflowActive();
        }
      }
    });
  }

  private boolean matchesRecovery(InstanceWorkflowState state, InstancePhase phase, long generation) {
    return recoveringPhase == phase && state.phase() == phase && state.pollingGeneration() == generation;
  }

  private void scheduleRecovery(InstanceWorkflowState state, int attempt) {
    Duration delay = state.phase().isDeleting() ? settings.pollMaxDelay() : switch (state.phase()) {
      case PREPARING_FORWARDING -> settings.pollDelay(attempt);
      default -> settings.pollInitialDelay();
    };
    timers.startSingleTimer(RECOVERY_TIMER, new RetryRecovery(state.phase(), state.pollingGeneration(), attempt), delay);
  }

  private void finishRecovery() {
    timers.cancel(RECOVERY_TIMER);
    recoveringPhase = null;
    recoveryRequestToken = null;
  }

  private void continueCreationAfterRecovery(InstanceWorkflowState state) {
    // The recovered operation is already persisted. Only the next unstarted operation uses creation logic.
    switch (state.phase()) {
      case RESERVING_RESOURCES -> requestResourceReservation(state);
      case PREPARING_DEPENDENCIES -> requestDependencies(state);
      case PREPARING_SECURITY_GROUP -> startSecurityGroupPreparation(state);
      case PREPARING_PARENT_PORT -> requestParentPort(state);
      case PREPARING_SUB_PORT -> requestSubPort(state);
      case PREPARING_TRUNK -> requestTrunk(state);
      case PREPARING_FORWARDING -> startForwardingPreparation(state);
      case PROVISIONING -> requestNovaCreation(state);
      case CONFIRMING -> startConfirm(state);
      default -> throw new IllegalStateException("Cannot continue creation after recovery: " + state.phase());
    }
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
