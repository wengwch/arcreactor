package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.instance.command.CheckForwardingStatus;
import cn.veryai.arcreactor.actor.instance.command.CheckNetwork;
import cn.veryai.arcreactor.actor.instance.command.CheckNovaStatus;
import cn.veryai.arcreactor.actor.instance.command.CheckProjectStatus;
import cn.veryai.arcreactor.actor.instance.command.CheckShareStatus;
import cn.veryai.arcreactor.actor.instance.command.CompensationCompleted;
import cn.veryai.arcreactor.actor.instance.command.ConfirmRejected;
import cn.veryai.arcreactor.actor.instance.command.ConfirmSucceeded;
import cn.veryai.arcreactor.actor.instance.command.ConfirmUnknown;
import cn.veryai.arcreactor.actor.instance.command.CreateInstance;
import cn.veryai.arcreactor.actor.instance.command.ForwardingPreparationTimedOut;
import cn.veryai.arcreactor.actor.instance.command.ForwardingStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.GetInstanceState;
import cn.veryai.arcreactor.actor.instance.command.NetworkStepCompleted;
import cn.veryai.arcreactor.actor.instance.command.NovaCreateFailed;
import cn.veryai.arcreactor.actor.instance.command.NovaCreateSucceeded;
import cn.veryai.arcreactor.actor.instance.command.NovaCreateUnknown;
import cn.veryai.arcreactor.actor.instance.command.NovaReconcileFound;
import cn.veryai.arcreactor.actor.instance.command.NovaReconcileNotFound;
import cn.veryai.arcreactor.actor.instance.command.NovaReconcileUnknown;
import cn.veryai.arcreactor.actor.instance.command.NovaStatusQueryFailed;
import cn.veryai.arcreactor.actor.instance.command.NovaStatusSucceeded;
import cn.veryai.arcreactor.actor.instance.command.ProjectPreparationTimedOut;
import cn.veryai.arcreactor.actor.instance.command.ProjectStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.ReconcileNova;
import cn.veryai.arcreactor.actor.instance.command.RetryCompensation;
import cn.veryai.arcreactor.actor.instance.command.RetryConfirm;
import cn.veryai.arcreactor.actor.instance.command.SchedulingFailed;
import cn.veryai.arcreactor.actor.instance.command.SchedulingSucceeded;
import cn.veryai.arcreactor.actor.instance.command.SharePreparationTimedOut;
import cn.veryai.arcreactor.actor.instance.command.ShareStatusQueryFailed;
import cn.veryai.arcreactor.actor.instance.command.ShareStatusSucceeded;
import cn.veryai.arcreactor.actor.instance.command.SharedNetworkStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.StartCompensation;
import cn.veryai.arcreactor.actor.instance.command.UserNetworkStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.CompensationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCandidateSelected;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPrepared;
import cn.veryai.arcreactor.actor.instance.event.ForwardingStatusCheckStarted;
import cn.veryai.arcreactor.actor.instance.event.HypervisorSelected;
import cn.veryai.arcreactor.actor.instance.event.InstanceActivated;
import cn.veryai.arcreactor.actor.instance.event.InstanceCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceProvisioningFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.event.NetworkCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.NetworkPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.NetworkResourcePrepared;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationRetried;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationStarted;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationUnknown;
import cn.veryai.arcreactor.actor.instance.event.NovaEnteredError;
import cn.veryai.arcreactor.actor.instance.event.NovaReachedActive;
import cn.veryai.arcreactor.actor.instance.event.NovaServerCreated;
import cn.veryai.arcreactor.actor.instance.event.NovaStatusChanged;
import cn.veryai.arcreactor.actor.instance.event.ProjectPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ProjectPrepared;
import cn.veryai.arcreactor.actor.instance.event.ProjectStatusCheckStarted;
import cn.veryai.arcreactor.actor.instance.event.ProvisioningDeadlineReached;
import cn.veryai.arcreactor.actor.instance.event.ReservationConfirmedForInstance;
import cn.veryai.arcreactor.actor.instance.event.ResourcesReservedForInstance;
import cn.veryai.arcreactor.actor.instance.event.SchedulingStarted;
import cn.veryai.arcreactor.actor.instance.event.SharePreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.SharePrepared;
import cn.veryai.arcreactor.actor.instance.event.ShareStatusCheckStarted;
import cn.veryai.arcreactor.actor.instance.event.SharedNetworkPrepared;
import cn.veryai.arcreactor.actor.instance.event.UserNetworkPrepared;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.NetworkPreparation;
import cn.veryai.arcreactor.actor.instance.model.NetworkRequirement;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.actor.instance.model.PreparedShare;
import cn.veryai.arcreactor.actor.instance.model.ShareRequirement;
import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import cn.veryai.arcreactor.actor.user.UserResources;
import cn.veryai.arcreactor.actor.user.model.UserNetworkState;
import cn.veryai.arcreactor.actor.user.model.UserShareState;
import cn.veryai.arcreactor.actor.user.reply.NetworkReply;
import cn.veryai.arcreactor.actor.user.reply.ProjectReply;
import cn.veryai.arcreactor.actor.user.reply.ShareReply;
import cn.veryai.arcreactor.entity.SysNetworkEntity;
import cn.veryai.arcreactor.enums.SysNetworkType;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import cn.veryai.arcreactor.repo.SysNetworkRepo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
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
import org.openstack4j.api.exceptions.ResponseException;
import org.openstack4j.model.network.Network;
import org.openstack4j.model.network.Subnet;

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
  private static final String SHARE_POLL_TIMER = "share-status-poll";
  private static final String SHARE_DEADLINE_TIMER = "share-preparation-deadline";
  private static final String FORWARDING_POLL_TIMER = "forwarding-status-poll";
  private static final String FORWARDING_DEADLINE_TIMER = "forwarding-preparation-deadline";
  private static final String NETWORK_TIMER = "network-preparation";
  private static final String PROJECT_POLL_TIMER = "project-status-poll";
  private static final String PROJECT_DEADLINE_TIMER = "project-preparation-deadline";
  private static final Executor NETWORK_DEPENDENCY_EXECUTOR =
      task -> Thread.ofVirtual().name("instance-network-dependency").start(task);
  private static final Executor CLOUD_INIT_EXECUTOR =
      task -> Thread.ofVirtual().name("instance-cloud-init").start(task);
  private record SharedNetworkIds(String networkId, String subnetId) {}

  private final String instanceId;
  private final ActorContext<WorkflowCommand> context;
  private final TimerScheduler<WorkflowCommand> timers;
  private final HypervisorPlacement placement;
  private final OpenStackClient openStackClient;
  private final UserResources userResources;
  private final SysNetworkRepo sysNetworkRepo;
  private final CloudInitRenderer cloudInitRenderer;
  private final FloatingIpForwardings floatingIpForwardings;
  private final InstanceWorkflowSettings settings;
  private final Clock clock;
  private final InstanceMetrics metrics = InstanceMetrics.NOOP;
  private boolean shareRequestInFlight;
  private boolean forwardingRequestInFlight;
  private boolean networkRequestInFlight;
  private boolean projectRequestInFlight;

  public static Behavior<WorkflowCommand> create(
      String instanceId,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      UserResources userResources,
      SysNetworkRepo sysNetworkRepo,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceWorkflowSettings settings) {
    return create(instanceId, placement, openStackClient, userResources, sysNetworkRepo, cloudInitRenderer,
        floatingIpForwardings, settings, Clock.systemUTC());
  }

  static Behavior<WorkflowCommand> create(
      String instanceId,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      UserResources userResources,
      SysNetworkRepo sysNetworkRepo,
      CloudInitRenderer cloudInitRenderer,
      FloatingIpForwardings floatingIpForwardings,
      InstanceWorkflowSettings settings,
      Clock clock) {
    return Behaviors.setup(
        context ->
            Behaviors.withTimers(
                timers ->
                    new InstanceWorkflowActor(
                        instanceId, context, timers, placement, openStackClient, userResources, sysNetworkRepo,
                        cloudInitRenderer, floatingIpForwardings, settings, clock)));
  }

  private InstanceWorkflowActor(
      String instanceId,
      ActorContext<WorkflowCommand> context,
      TimerScheduler<WorkflowCommand> timers,
      HypervisorPlacement placement,
      OpenStackClient openStackClient,
      UserResources userResources,
      SysNetworkRepo sysNetworkRepo,
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
    this.userResources = Objects.requireNonNull(userResources);
    this.sysNetworkRepo = Objects.requireNonNull(sysNetworkRepo);
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
        .onCommand(CheckProjectStatus.class, this::onCheckProjectStatus)
        .onCommand(ProjectStatusCompleted.class, this::onProjectStatusCompleted)
        .onCommand(ProjectPreparationTimedOut.class, this::onProjectPreparationTimedOut)
        .onCommand(CheckNetwork.class, this::onCheckNetwork)
        .onCommand(UserNetworkStatusCompleted.class, this::onUserNetworkStatusCompleted)
        .onCommand(SharedNetworkStatusCompleted.class, this::onSharedNetworkStatusCompleted)
        .onCommand(NetworkStepCompleted.class, this::onNetworkStepCompleted)
        .onCommand(CheckShareStatus.class, this::onCheckShareStatus)
        .onCommand(ShareStatusSucceeded.class, this::onShareStatusSucceeded)
        .onCommand(ShareStatusQueryFailed.class, this::onShareStatusQueryFailed)
        .onCommand(SharePreparationTimedOut.class, this::onSharePreparationTimedOut)
        .onCommand(CheckForwardingStatus.class, this::onCheckForwardingStatus)
        .onCommand(ForwardingStatusCompleted.class, this::onForwardingStatusCompleted)
        .onCommand(ForwardingPreparationTimedOut.class, this::onForwardingPreparationTimedOut)
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
    if (command.spec().share() == null) {
      command.replyTo().tell(InstanceReply.rejected("share dependency is required"));
      return Effect().none();
    }
    if (command.spec().network() == null) {
      command.replyTo().tell(InstanceReply.rejected("network dependency is required"));
      return Effect().none();
    }
    if (command.spec().network().userNetwork() == null) {
      command.replyTo().tell(InstanceReply.rejected("user network specification is required for subport creation"));
      return Effect().none();
    }
    if (command.spec().cloudInit() == null) {
      command.replyTo().tell(InstanceReply.rejected("cloud-init credentials and settings are required"));
      return Effect().none();
    }
    Instant now = clock.instant();
    return Effect()
        .persist(
            List.of(
                new InstanceCreationRequested(instanceId, command.requestId(), command.spec(), now),
                new ProjectPreparationStarted(instanceId, now.plus(settings.projectPreparationTimeout()))))
        .thenRun(
            next -> {
              metrics.workflowStarted();
              command.replyTo().tell(InstanceReply.accepted(false));
              startProjectPreparation(next);
            });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onGetState(
      InstanceWorkflowState state, GetInstanceState command) {
    command.replyTo().tell(state);
    return Effect().none();
  }

  private void startProjectPreparation(InstanceWorkflowState state) {
    scheduleProjectDeadline(state);
    // Reuse this user's regional project, including projects created with a custom name.
    if (!projectWaitExpired(state)) requestProject(state, 1, false);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckProjectStatus(
      InstanceWorkflowState state, CheckProjectStatus command) {
    if (!matchesProjectRequest(state, command.generation()) || projectRequestInFlight) return Effect().none();
    if (projectWaitExpired(state)) return failProjectPreparation("PROJECT_PREPARATION_TIMEOUT",
        "Timeout waiting for the user project");
    return Effect().persist(new ProjectStatusCheckStarted(instanceId))
        .thenRun(next -> requestProject(next, command.attempt(), false));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onProjectStatusCompleted(
      InstanceWorkflowState state, ProjectStatusCompleted command) {
    if (!matchesProjectRequest(state, command.generation())) return Effect().none();
    projectRequestInFlight = false;
    if (projectWaitExpired(state)) return failProjectPreparation("PROJECT_PREPARATION_TIMEOUT",
        "Timeout waiting for the user project");
    ProjectReply reply = command.reply();
    if (reply == null) {
      scheduleProjectPoll(state, command.attempt() + 1);
      return Effect().none();
    }
    return switch (reply.status()) {
      case READY -> {
        if (!state.spec().share().userId().equals(reply.userId())
            || !state.spec().region().equals(reply.regionId()) || !hasText(reply.projectId())
            || (state.spec().projectId() != null && !state.spec().projectId().equals(reply.projectId()))) {
          yield failProjectPreparation("PROJECT_RESULT_INVALID", "Project readiness result is incomplete or mismatched");
        }
        Instant now = clock.instant();
        yield Effect().persist(List.of(new ProjectPrepared(instanceId, reply.projectId(), now),
                new SharePreparationStarted(instanceId, now.plus(settings.sharePreparationTimeout()))))
            .thenRun(next -> { cancelProjectTimers(); startSharePreparation(next); });
      }
      case FAILED -> failProjectPreparation("PROJECT_PREPARATION_FAILED", reply.detail());
      case REJECTED -> failProjectPreparation("PROJECT_PREPARATION_REJECTED", reply.detail());
      case NOT_FOUND -> {
        if (command.submission()) {
          scheduleProjectPoll(state, command.attempt() + 1);
          yield Effect().none();
        }
        // UserActor serializes concurrent submissions for this user and region.
        yield Effect().persist(new ProjectStatusCheckStarted(instanceId))
            .thenRun(next -> requestProject(next, command.attempt(), true));
      }
      case IN_PROGRESS -> {
        scheduleProjectPoll(state, command.attempt() + 1);
        yield Effect().none();
      }
    };
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onProjectPreparationTimedOut(
      InstanceWorkflowState state, ProjectPreparationTimedOut command) {
    if (state.phase() != InstancePhase.PREPARING_PROJECT
        || !Objects.equals(state.projectPreparation().deadline(), command.deadline())) return Effect().none();
    if (!projectWaitExpired(state)) {
      scheduleProjectDeadline(state);
      return Effect().none();
    }
    return failProjectPreparation("PROJECT_PREPARATION_TIMEOUT", "Timeout waiting for the user project");
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> failProjectPreparation(String code, String detail) {
    return Effect().persist(new InstanceProvisioningFailed(instanceId, code, detail, clock.instant()))
        .thenRun(next -> {
          cancelProjectTimers();
          // UserActor owns the project; no instance-specific resource has been allocated yet.
          metrics.workflowFailed();
        });
  }

  private void requestProject(InstanceWorkflowState state, int attempt, boolean submission) {
    projectRequestInFlight = true;
    CompletionStage<ProjectReply> request;
    try {
      String userId = state.spec().share().userId();
      request = submission ? userResources.createProject(userId, state.spec().region(), state.spec().projectName())
          : userResources.getProject(userId, state.spec().region());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (reply, error) -> new ProjectStatusCompleted(
        state.pollingGeneration(), attempt, submission, error == null ? reply : null));
  }

  private static boolean matchesProjectRequest(InstanceWorkflowState state, long generation) {
    return state.phase() == InstancePhase.PREPARING_PROJECT && state.pollingGeneration() == generation;
  }

  private boolean projectWaitExpired(InstanceWorkflowState state) {
    return !clock.instant().isBefore(state.projectPreparation().deadline());
  }

  private void scheduleProjectPoll(InstanceWorkflowState state, int attempt) {
    timers.startSingleTimer(PROJECT_POLL_TIMER,
        new CheckProjectStatus(state.pollingGeneration(), attempt), settings.pollDelay(attempt));
  }

  private void scheduleProjectDeadline(InstanceWorkflowState state) {
    Duration remaining = Duration.between(clock.instant(), state.projectPreparation().deadline());
    timers.startSingleTimer(PROJECT_DEADLINE_TIMER,
        new ProjectPreparationTimedOut(state.projectPreparation().deadline()),
        remaining.isNegative() || remaining.isZero() ? Duration.ofMillis(1) : remaining);
  }

  private void cancelProjectTimers() {
    timers.cancel(PROJECT_POLL_TIMER);
    timers.cancel(PROJECT_DEADLINE_TIMER);
    projectRequestInFlight = false;
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckShareStatus(
      InstanceWorkflowState state, CheckShareStatus command) {
    if (!matchesShareRequest(state, command.generation()) || shareRequestInFlight) {
      return Effect().none();
    }
    if (shareWaitExpired(state)) return failSharePreparation("SHARE_PREPARATION_TIMEOUT",
        "Timeout waiting for the shared storage dependency");
    return Effect().persist(new ShareStatusCheckStarted(instanceId))
        .thenRun(next -> requestShare(next, command.attempt(), false));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onShareStatusSucceeded(
      InstanceWorkflowState state, ShareStatusSucceeded command) {
    if (!matchesShareRequest(state, command.generation())) return Effect().none();
    shareRequestInFlight = false;
    if (shareWaitExpired(state)) return failSharePreparation("SHARE_PREPARATION_TIMEOUT",
        "Timeout waiting for the shared storage dependency");
    ShareReply reply = command.reply();
    if (reply == null) {
      scheduleSharePoll(state, command.attempt() + 1);
      return Effect().none();
    }
    return switch (reply.status()) {
      case READY -> {
        UserShareState share = reply.share();
        if (share == null || share.phase() != UserShareState.Phase.READY
            || !state.shareSpec().equals(share.spec())
            || !hasText(share.osShareId()) || !hasText(share.osExportLocationId())
            || !hasText(share.exportLocationPath()) || !hasText(share.osGrantAccessId())
            || !hasText(share.accessKey())) {
          yield failSharePreparation("SHARE_RESULT_INVALID", "Share readiness result is incomplete or mismatched");
        }
        Instant now = clock.instant();
        InstanceWorkflowEvent nextStage = state.spec().network() == null
            ? afterNetworkStage(state, now)
            : new NetworkPreparationStarted(instanceId, now.plus(settings.provisioningTimeout()));
        yield Effect().persist(List.of(
                new SharePrepared(instanceId, PreparedShare.from(share), now),
                nextStage))
            .thenRun(next -> {
              cancelShareTimers();
              if (next.phase() == InstancePhase.PREPARING_NETWORK) startNetworkPreparation(next);
              else startAfterNetwork(next);
            });
      }
      case FAILED -> failSharePreparation("SHARE_PREPARATION_FAILED", reply.detail());
      case REJECTED -> failSharePreparation("SHARE_PREPARATION_REJECTED", reply.detail());
      case NOT_FOUND -> {
        if (command.submission()) {
          scheduleSharePoll(state, command.attempt() + 1);
          yield Effect().none();
        }
        // A crash may have occurred before the original submission reached UserActor.
        yield Effect().persist(new ShareStatusCheckStarted(instanceId))
            .thenRun(next -> requestShare(next, command.attempt(), true));
      }
      case IN_PROGRESS -> {
        scheduleSharePoll(state, command.attempt() + 1);
        yield Effect().none();
      }
    };
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onShareStatusQueryFailed(
      InstanceWorkflowState state, ShareStatusQueryFailed command) {
    if (!matchesShareRequest(state, command.generation())) return Effect().none();
    shareRequestInFlight = false;
    if (shareWaitExpired(state)) return failSharePreparation("SHARE_PREPARATION_TIMEOUT",
        "Timeout waiting for the shared storage dependency");
    // An ask timeout says nothing about the outcome of the shared resource creation.
    scheduleSharePoll(state, command.attempt() + 1);
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSharePreparationTimedOut(
      InstanceWorkflowState state, SharePreparationTimedOut command) {
    if (state.phase() != InstancePhase.PREPARING_SHARE
        || !Objects.equals(state.sharePreparationDeadline(), command.deadline())) return Effect().none();
    if (!shareWaitExpired(state)) {
      scheduleShareDeadline(state);
      return Effect().none();
    }
    return failSharePreparation("SHARE_PREPARATION_TIMEOUT", "Timeout waiting for the shared storage dependency");
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> failSharePreparation(String code, String detail) {
    return Effect().persist(new InstanceProvisioningFailed(instanceId, code, detail, clock.instant()))
        .thenRun(next -> {
          cancelShareTimers();
          // No host has been reserved yet, and shared storage remains owned by UserActor.
          metrics.workflowFailed();
        });
  }

  private void startSharePreparation(InstanceWorkflowState state) {
    scheduleShareDeadline(state);
    if (!shareWaitExpired(state)) requestShare(state, 1, true);
  }

  private void requestShare(InstanceWorkflowState state, int attempt, boolean submission) {
    ShareRequirement required = state.spec().share();
    shareRequestInFlight = true;
    CompletionStage<ShareReply> request;
    try {
      request = submission
          ? userResources.createShare(required.userId(), required.shareId(), state.shareSpec())
          : userResources.getShare(required.userId(), required.spec().regionId());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (reply, error) -> error == null
        ? new ShareStatusSucceeded(state.pollingGeneration(), attempt, submission, reply)
        : new ShareStatusQueryFailed(state.pollingGeneration(), attempt));
  }

  private void scheduleSharePoll(InstanceWorkflowState state, int attempt) {
    timers.startSingleTimer(SHARE_POLL_TIMER,
        new CheckShareStatus(state.pollingGeneration(), attempt), settings.pollDelay(attempt));
  }

  private void scheduleShareDeadline(InstanceWorkflowState state) {
    Duration remaining = Duration.between(clock.instant(), state.sharePreparationDeadline());
    timers.startSingleTimer(SHARE_DEADLINE_TIMER,
        new SharePreparationTimedOut(state.sharePreparationDeadline()),
        remaining.isNegative() || remaining.isZero() ? Duration.ofMillis(1) : remaining);
  }

  private void cancelShareTimers() {
    timers.cancel(SHARE_POLL_TIMER);
    timers.cancel(SHARE_DEADLINE_TIMER);
    shareRequestInFlight = false;
  }

  private boolean shareWaitExpired(InstanceWorkflowState state) {
    return !clock.instant().isBefore(state.sharePreparationDeadline());
  }

  private static boolean matchesShareRequest(InstanceWorkflowState state, long generation) {
    return state.phase() == InstancePhase.PREPARING_SHARE && state.pollingGeneration() == generation;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private InstanceWorkflowEvent afterNetworkStage(InstanceWorkflowState state, Instant now) {
    return state.spec().forwarding() == null
        ? new SchedulingStarted(instanceId, state.requestId(), 1, now)
        : new ForwardingPreparationStarted(instanceId, now.plus(settings.forwardingPreparationTimeout()));
  }

  private void startAfterNetwork(InstanceWorkflowState state) {
    if (state.phase() == InstancePhase.PREPARING_FORWARDING) startForwardingPreparation(state);
    else startScheduling(state);
  }

  private void startNetworkPreparation(InstanceWorkflowState state) {
    context.getSelf().tell(new CheckNetwork(state.pollingGeneration()));
  }

  private boolean matchesNetwork(InstanceWorkflowState state, long generation) {
    return state.phase() == InstancePhase.PREPARING_NETWORK && state.pollingGeneration() == generation;
  }

  private boolean networkWaitExpired(InstanceWorkflowState state) {
    return !clock.instant().isBefore(state.networkPreparation().deadline());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckNetwork(
      InstanceWorkflowState state, CheckNetwork command) {
    if (!matchesNetwork(state, command.generation())) return Effect().none();
    // Wait for an in-flight write to settle before cleanup can race it.
    if (networkRequestInFlight) {
      scheduleNetworkPoll(state);
      return Effect().none();
    }
    if (networkWaitExpired(state)) {
      return beginCompensation("NETWORK_PREPARATION_TIMEOUT", "Timed out preparing user network, instance ports and trunk");
    }
    if (state.networkPreparation().step() == NetworkPreparation.Step.USER_NETWORK) requestUserNetwork(state, false);
    else if (needsSharedNetwork(state)) requestSharedNetwork(state);
    else requestNetworkStep(state, false);
    return Effect().none();
  }

  private static boolean needsSharedNetwork(InstanceWorkflowState state) {
    NetworkPreparation preparation = state.networkPreparation();
    // Recover older in-flight writes using their original network IDs instead of moving an existing port.
    return preparation.step() == NetworkPreparation.Step.PARENT_PORT && !preparation.createRequested()
        && !hasText(preparation.resources().parentPortId())
        && (!hasText(preparation.sharedNetworkId()) || !hasText(preparation.sharedSubnetId()));
  }

  private void requestSharedNetwork(InstanceWorkflowState state) {
    networkRequestInFlight = true;
    scheduleNetworkPoll(state);
    long remainingMillis = Math.max(1,
        Duration.between(clock.instant(), state.networkPreparation().deadline()).toMillis());
    CompletableFuture<SharedNetworkIds> request = CompletableFuture.supplyAsync(
        () -> resolveSharedNetwork(state.spec().region(), state.projectId()), NETWORK_DEPENDENCY_EXECUTOR);
    context.pipeToSelf(request.orTimeout(remainingMillis, TimeUnit.MILLISECONDS), (ids, error) -> {
      if (error == null) {
        return new SharedNetworkStatusCompleted(state.pollingGeneration(), ids.networkId(), ids.subnetId(), false, null);
      }
      Throwable cause = rootCause(error);
      boolean rejected = cause instanceof OpenStackOperationException && cause.getCause() == null;
      if (cause instanceof ResponseException response) {
        int status = response.getStatus();
        rejected = status >= 400 && status < 500 && status != 408 && status != 429;
      }
      String detail = cause instanceof OpenStackOperationException && cause.getCause() == null
          ? cause.getMessage() : "Preset SHARED network lookup failed: " + cause.getClass().getSimpleName();
      return new SharedNetworkStatusCompleted(state.pollingGeneration(), null, null, rejected, detail);
    });
  }

  private SharedNetworkIds resolveSharedNetwork(String regionId, String projectId) {
    List<SysNetworkEntity> matches = sysNetworkRepo.findByRegionId(regionId).stream()
        .filter(network -> network.getSysNetworkType() == SysNetworkType.SHARED).toList();
    if (matches.size() != 1) {
      throw new OpenStackOperationException("Exactly one preset SHARED network is required in this region", null);
    }
    SysNetworkEntity preset = matches.getFirst();
    if (!preset.isShared() || !hasText(preset.getOsNetId()) || !hasText(preset.getOsSubNetId())) {
      throw new OpenStackOperationException("Preset SHARED network must be shared with OpenStack network and subnet IDs", null);
    }
    Network network = openStackClient.getNetwork(regionId, projectId, preset.getOsNetId());
    if (network == null || !network.isShared() || !preset.getOsNetId().equals(network.getId())) {
      throw new OpenStackOperationException("Preset SHARED network is not a shared Neutron network", null);
    }
    Subnet subnet = openStackClient.getSubnet(regionId, projectId, preset.getOsSubNetId());
    if (subnet == null || !preset.getOsSubNetId().equals(subnet.getId())
        || !network.getId().equals(subnet.getNetworkId())) {
      throw new OpenStackOperationException("Preset SHARED subnet does not belong to the configured network", null);
    }
    return new SharedNetworkIds(network.getId(), subnet.getId());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSharedNetworkStatusCompleted(
      InstanceWorkflowState state, SharedNetworkStatusCompleted command) {
    if (!matchesNetwork(state, command.generation()) || !needsSharedNetwork(state)) return Effect().none();
    networkRequestInFlight = false;
    if (networkWaitExpired(state)) {
      return beginCompensation("NETWORK_PREPARATION_TIMEOUT", "Timed out resolving the preset SHARED network");
    }
    if (command.rejected()) return beginCompensation("SHARED_NETWORK_PREPARATION_FAILED", command.detail());
    if (!hasText(command.networkId()) || !hasText(command.subnetId())) {
      scheduleNetworkPoll(state);
      return Effect().none();
    }
    return Effect().persist(new SharedNetworkPrepared(instanceId, command.networkId(), command.subnetId()))
        .thenRun(this::startNetworkPreparation);
  }

  private void requestUserNetwork(InstanceWorkflowState state, boolean submission) {
    networkRequestInFlight = true;
    scheduleNetworkPoll(state);
    CompletionStage<NetworkReply> request;
    try {
      String userId = state.spec().share().userId();
      request = submission ? userResources.createNetwork(userId, state.spec().network().userNetwork())
          : userResources.getNetwork(userId, state.spec().region());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (reply, error) -> new UserNetworkStatusCompleted(
        state.pollingGeneration(), submission, error == null ? reply : null));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onUserNetworkStatusCompleted(
      InstanceWorkflowState state, UserNetworkStatusCompleted command) {
    if (!matchesNetwork(state, command.generation())
        || state.networkPreparation().step() != NetworkPreparation.Step.USER_NETWORK) return Effect().none();
    networkRequestInFlight = false;
    if (networkWaitExpired(state)) {
      return beginCompensation("NETWORK_PREPARATION_TIMEOUT", "Timed out waiting for the user network");
    }
    NetworkReply reply = command.reply();
    if (reply == null) {
      // A timeout can follow a successful submission; query UserActor before submitting again.
      scheduleNetworkPoll(state);
      return Effect().none();
    }
    return switch (reply.status()) {
      case READY -> {
        UserNetworkState network = reply.network();
        if (!state.spec().share().userId().equals(reply.userId())
            || !state.spec().region().equals(reply.regionId())
            || network == null || network.phase() != UserNetworkState.Phase.READY
            || !state.spec().network().userNetwork().equals(network.spec())
            || !state.projectId().equals(network.projectId())
            || !hasText(network.networkId()) || !hasText(network.subnetId())
            || !hasText(network.routerId()) || !hasText(network.localNetworkId())
            || !hasText(network.routerPortId())) {
          yield beginCompensation("USER_NETWORK_RESULT_INVALID", "User network readiness result is incomplete or mismatched");
        }
        yield Effect().persist(new UserNetworkPrepared(instanceId, network.networkId(), network.subnetId()))
            .thenRun(this::startNetworkPreparation);
      }
      case FAILED -> beginCompensation("USER_NETWORK_PREPARATION_FAILED", reply.detail());
      case REJECTED -> beginCompensation("USER_NETWORK_PREPARATION_REJECTED", reply.detail());
      case NOT_FOUND -> {
        if (command.submission()) scheduleNetworkPoll(state);
        // UserActor serializes submissions from concurrent instances for this user and region.
        else requestUserNetwork(state, true);
        yield Effect().none();
      }
      case IN_PROGRESS -> {
        scheduleNetworkPoll(state);
        yield Effect().none();
      }
    };
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNetworkStepCompleted(
      InstanceWorkflowState state, NetworkStepCompleted command) {
    if (!matchesNetwork(state, command.generation())
        || state.networkPreparation().step() == NetworkPreparation.Step.USER_NETWORK
        || needsSharedNetwork(state)) return Effect().none();
    networkRequestInFlight = false;
    if (command.rejected()) return beginCompensation("NETWORK_PREPARATION_FAILED", command.detail());
    if (networkWaitExpired(state)) {
      return beginCompensation("NETWORK_PREPARATION_TIMEOUT", "Timed out preparing user network, instance ports and trunk");
    }
    if (command.resources() != null) {
      NetworkPreparation.Step step = state.networkPreparation().step();
      NetworkResourcePrepared prepared = new NetworkResourcePrepared(instanceId, step, command.resources());
      if (step == NetworkPreparation.Step.TRUNK) {
        return Effect().persist(List.of(prepared, afterNetworkStage(state, clock.instant())))
            .thenRun(next -> { timers.cancel(NETWORK_TIMER); startAfterNetwork(next); });
      }
      return Effect().persist(prepared).thenRun(this::startNetworkPreparation);
    }
    if (!state.networkPreparation().createRequested() && command.detail() == null) {
      // Journal intent before sending the write. Recovery never blindly repeats an uncertain create.
      return Effect().persist(new NetworkCreationRequested(instanceId))
          .thenRun(next -> requestNetworkStep(next, true));
    }
    scheduleNetworkPoll(state);
    return Effect().none();
  }

  private void requestNetworkStep(InstanceWorkflowState state, boolean allowCreate) {
    networkRequestInFlight = true;
    scheduleNetworkPoll(state);
    CompletionStage<PreparedNetwork> request;
    try {
      request = openStackClient.ensureInstanceNetworkStep(target(state), networkOperationId(state),
          resolvedNetworkRequirement(state), state.networkPreparation().step(), state.networkPreparation().resources(), allowCreate);
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (resources, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new NetworkStepCompleted(state.pollingGeneration(), resources,
          cause instanceof OpenStackOperationException, cause == null ? null : cause.toString());
    });
  }

  private static NetworkRequirement resolvedNetworkRequirement(InstanceWorkflowState state) {
    NetworkRequirement requirement = state.spec().network();
    NetworkPreparation preparation = state.networkPreparation();
    if (hasText(preparation.sharedNetworkId()) && hasText(preparation.sharedSubnetId())) {
      requirement = requirement.withParentNetwork(preparation.sharedNetworkId(), preparation.sharedSubnetId());
    } else if (!hasText(requirement.parentNetworkId()) || !hasText(requirement.parentSubnetId())
        || (!preparation.createRequested() && !hasText(preparation.resources().parentPortId()))) {
      throw new OpenStackOperationException("Preset SHARED network must be resolved before preparing the parent port", null);
    }
    if (requirement.userNetwork() == null) return requirement; // Existing workflows retain their recorded subnet IDs.
    if (preparation.step() == NetworkPreparation.Step.USER_NETWORK
        || !hasText(preparation.userNetworkId()) || !hasText(preparation.userSubnetId())) {
      throw new OpenStackOperationException("User network must be READY before preparing instance ports", null);
    }
    return requirement.withSubNetwork(preparation.userNetworkId(), preparation.userSubnetId());
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
    if (!matchesForwarding(state, command.generation()) || forwardingRequestInFlight) return Effect().none();
    if (forwardingWaitExpired(state)) return beginCompensation("FORWARDING_PREPARATION_TIMEOUT", "Timed out preparing port forwarding");
    return Effect().persist(new ForwardingStatusCheckStarted(instanceId))
        .thenRun(next -> requestForwarding(next, command.attempt(), false));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onForwardingStatusCompleted(
      InstanceWorkflowState state, ForwardingStatusCompleted command) {
    if (!matchesForwarding(state, command.generation())) return Effect().none();
    forwardingRequestInFlight = false;
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
        yield Effect().persist(new ForwardingStatusCheckStarted(instanceId))
            .thenRun(next -> requestForwarding(next, command.attempt(), true));
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
    forwardingRequestInFlight = true;
    CompletionStage<ForwardingReply> request;
    try {
      request = submission ? floatingIpForwardings.create(forwardingFloatingIpId(state), forwardingClaimId(state), forwardingSpec(state))
          : floatingIpForwardings.get(state.spec().region(), forwardingFloatingIpId(state), forwardingClaimId(state));
    } catch (RuntimeException error) { request = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(request, (reply, error) -> new ForwardingStatusCompleted(
        state.pollingGeneration(), attempt, submission, error == null ? reply : null));
  }

  private ForwardingSpec forwardingSpec(InstanceWorkflowState state) {
    if (state.networkPreparation() != null) {
      PreparedNetwork network = state.networkPreparation().resources();
      var required = state.spec().forwarding();
      return new ForwardingSpec(instanceId, state.spec().share().userId(), state.spec().region(),
          state.projectId(), network.parentPortId(), network.parentFixedIp(), required.internalPort(),
          required.securityGroupId());
    }
    return state.spec().forwarding().toSpec(instanceId, state.spec().share().userId(),
        state.spec().region(), state.projectId());
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
    forwardingRequestInFlight = false;
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
                new ResourcesReservedForInstance(
                    instanceId, result.hypervisorId(), reservationId, state.spec(), now),
                new NovaCreationStarted(
                    instanceId, operationId, now, now.plus(settings.provisioningTimeout()))))
        .thenRun(this::startNovaCreate);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSchedulingFailed(
      InstanceWorkflowState state, SchedulingFailed command) {
    if (state.phase() != InstancePhase.SCHEDULING) return Effect().none();
    return beginCompensation("SCHEDULING_FAILED", command.reason());
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
                OpenStackClient.NovaServer.Status.BUILD,
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
          if (result.status() == OpenStackClient.ServerLookupResult.Status.NOT_FOUND) {
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
    OpenStackClient.NovaServer server = command.server();
    if ((server.status() == OpenStackClient.NovaServer.Status.BUILD
            || server.status() == OpenStackClient.NovaServer.Status.OTHER)
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
    if (server.status() == OpenStackClient.NovaServer.Status.ACTIVE) {
      events.add(new NovaReachedActive(instanceId, server.serverId(), clock.instant()));
      return Effect().persist(events).thenRun(this::startConfirm);
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ERROR) {
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
              timers.cancel(NETWORK_TIMER);
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

  private void startNovaCreate(InstanceWorkflowState state) {
    CompletionStage<String> rendered;
    try {
      if (state.spec().cloudInit() == null) {
        // Resume older persisted workflows with their original boot behavior.
        rendered = CompletableFuture.completedFuture(null);
      } else {
        Map<String, Object> model = cloudInitModel(state);
        rendered = CompletableFuture.supplyAsync(() -> cloudInitRenderer.render(model), CLOUD_INIT_EXECUTOR);
      }
    } catch (RuntimeException error) {
      context.getSelf().tell(new NovaCreateFailed("Cloud-init input data is incomplete"));
      return;
    }
    context.pipeToSelf(rendered.thenCompose(userData -> createNovaServer(state, userData)), (result, error) -> {
      if (error == null) return new NovaCreateSucceeded(result.serverId());
      Throwable cause = rootCause(error);
      if (cause instanceof CloudInitRenderer.RenderingException) return new NovaCreateFailed(cause.getMessage());
      return cause instanceof OpenStackOperationException
          ? new NovaCreateFailed(cause.getMessage())
          : new NovaCreateUnknown(state.novaOperationId(), cause.toString());
    });
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
            state.networkPreparation() != null ? state.networkPreparation().resources().parentPortId()
                : spec.forwarding() == null ? null : spec.forwarding().parentPortId(),
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
            ? CompletableFuture.completedFuture(true)
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
        .onEvent(ProjectPreparationStarted.class, (state, event) -> state.preparingProject(event.deadline()))
        .onEvent(ProjectStatusCheckStarted.class, (state, event) -> state.dependencyCheckStarted())
        .onEvent(ProjectPrepared.class, (state, event) -> state.projectPrepared(event.projectId()))
        .onEvent(SharePreparationStarted.class, (state, event) -> state.preparingShare(event.deadline()))
        .onEvent(ShareStatusCheckStarted.class, (state, event) -> state.dependencyCheckStarted())
        .onEvent(SharePrepared.class, (state, event) -> state.sharePrepared(event.share()))
        .onEvent(NetworkPreparationStarted.class, (state, event) -> state.preparingNetwork(event.deadline(),
            state.spec().network().userNetwork() == null
                ? NetworkPreparation.Step.PARENT_PORT : NetworkPreparation.Step.USER_NETWORK))
        .onEvent(UserNetworkPrepared.class, (state, event) -> state.networkPrepared(new NetworkPreparation(
            state.networkPreparation().deadline(), NetworkPreparation.Step.PARENT_PORT, false,
            state.networkPreparation().resources(), event.networkId(), event.subnetId(),
            state.networkPreparation().sharedNetworkId(), state.networkPreparation().sharedSubnetId())))
        .onEvent(SharedNetworkPrepared.class, (state, event) -> state.networkPrepared(new NetworkPreparation(
            state.networkPreparation().deadline(), NetworkPreparation.Step.PARENT_PORT, false,
            state.networkPreparation().resources(), state.networkPreparation().userNetworkId(),
            state.networkPreparation().userSubnetId(), event.networkId(), event.subnetId())))
        .onEvent(NetworkCreationRequested.class, (state, event) -> state.networkRequested())
        .onEvent(NetworkResourcePrepared.class, this::applyNetworkResourcePrepared)
        .onEvent(ForwardingPreparationStarted.class, (state, event) -> state.preparingForwarding(event.deadline()))
        .onEvent(ForwardingCandidateSelected.class, (state, event) -> state.forwardingCandidateSelected(event.candidateIndex()))
        .onEvent(ForwardingStatusCheckStarted.class, (state, event) -> state.dependencyCheckStarted())
        .onEvent(ForwardingPrepared.class, (state, event) -> state.forwardingPrepared(event.forwarding()))
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
            (state, event) -> state.novaStatus(OpenStackClient.NovaServer.Status.ERROR))
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

  private InstanceWorkflowState applyNetworkResourcePrepared(InstanceWorkflowState state, NetworkResourcePrepared event) {
    NetworkPreparation.Step nextStep = switch (event.step()) {
      case PARENT_PORT -> NetworkPreparation.Step.SUB_PORT;
      case SUB_PORT -> NetworkPreparation.Step.TRUNK;
      case TRUNK, COMPLETE -> NetworkPreparation.Step.COMPLETE;
      case USER_NETWORK -> throw new IllegalStateException("User network readiness requires UserNetworkPrepared");
    };
    NetworkPreparation preparation = state.networkPreparation();
    return state.networkPrepared(new NetworkPreparation(preparation.deadline(), nextStep, false,
        event.resources(), preparation.userNetworkId(), preparation.userSubnetId(),
        preparation.sharedNetworkId(), preparation.sharedSubnetId()));
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
                case PREPARING_PROJECT -> {
                  scheduleProjectDeadline(state);
                  context.getSelf().tell(new CheckProjectStatus(state.pollingGeneration(), 1));
                }
                case PREPARING_NETWORK -> startNetworkPreparation(state);
                case PREPARING_FORWARDING -> {
                  scheduleForwardingDeadline(state);
                  context.getSelf().tell(new CheckForwardingStatus(state.pollingGeneration(), 1));
                }
                case PREPARING_SHARE -> {
                  scheduleShareDeadline(state);
                  context.getSelf().tell(new CheckShareStatus(state.pollingGeneration(), 1));
                }
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
