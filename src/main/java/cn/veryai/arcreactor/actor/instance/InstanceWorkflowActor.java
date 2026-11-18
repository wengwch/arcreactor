package cn.veryai.arcreactor.actor.instance;

import static cn.veryai.arcreactor.actor.instance.InstanceOperationIds.*;

import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import cn.veryai.arcreactor.actor.instance.command.CreateInstanceForwarding;
import cn.veryai.arcreactor.actor.instance.command.DeleteInstanceForwarding;
import cn.veryai.arcreactor.actor.instance.command.UpdateInstanceQos;
import cn.veryai.arcreactor.actor.instance.command.ForwardingScope;
import cn.veryai.arcreactor.actor.instance.command.QosUpdateCompleted;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingSpec.Phase;
import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCreated;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCreationFailed;
import cn.veryai.arcreactor.actor.instance.event.UserForwardingDeletionRequested;
import cn.veryai.arcreactor.actor.instance.event.UserForwardingDeleted;
import cn.veryai.arcreactor.actor.instance.event.UserForwardingDeletionFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdated;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateFailed;
import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
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
import cn.veryai.arcreactor.actor.instance.command.ForwardingsDeletionCompleted;
import cn.veryai.arcreactor.actor.instance.command.InstanceNetworkDeletionCompleted;
import cn.veryai.arcreactor.actor.instance.command.InstanceSecurityGroupDeletionCompleted;
import cn.veryai.arcreactor.actor.instance.command.ReservationCancellationCompleted;
import cn.veryai.arcreactor.actor.instance.command.ResourceReservationCompleted;
import cn.veryai.arcreactor.actor.instance.command.RetryResourceReservation;
import cn.veryai.arcreactor.actor.instance.command.DependenciesFailed;
import cn.veryai.arcreactor.actor.instance.command.DependenciesReady;
import cn.veryai.arcreactor.actor.instance.command.ForwardingStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.GetInstanceState;
import cn.veryai.arcreactor.actor.instance.command.NetworkPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.NovaCreationCompleted;
import cn.veryai.arcreactor.actor.instance.command.NovaDeletionChecked;
import cn.veryai.arcreactor.actor.instance.command.NovaDeletionSubmitted;
import cn.veryai.arcreactor.actor.instance.command.ProvisioningChecked;
import cn.veryai.arcreactor.actor.instance.command.RecoveryStepCompleted;
import cn.veryai.arcreactor.actor.instance.command.RetryConfirm;
import cn.veryai.arcreactor.actor.instance.command.RetryRecovery;
import cn.veryai.arcreactor.actor.instance.command.SchedulingFailed;
import cn.veryai.arcreactor.actor.instance.command.SchedulingSucceeded;
import cn.veryai.arcreactor.actor.instance.command.SecurityGroupPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.DependenciesPrepared;
import cn.veryai.arcreactor.actor.instance.event.DependencyPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCandidateSelected;
import cn.veryai.arcreactor.actor.instance.event.ForwardingPreparationStarted;
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
import cn.veryai.arcreactor.actor.instance.event.NetworkPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.NetworkPrepared;
import cn.veryai.arcreactor.actor.instance.event.NovaCreationStarted;
import cn.veryai.arcreactor.actor.instance.event.NovaDeletionAccepted;
import cn.veryai.arcreactor.actor.instance.event.NovaServerCreated;
import cn.veryai.arcreactor.actor.instance.event.ReservationConfirmedForInstance;
import cn.veryai.arcreactor.actor.instance.event.SchedulingStarted;
import cn.veryai.arcreactor.actor.instance.event.SecurityGroupPreparationStarted;
import cn.veryai.arcreactor.actor.instance.event.SecurityGroupPrepared;
import cn.veryai.arcreactor.actor.instance.event.SshConnectivityVerified;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.ForwardingDeletionTarget;
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
import cn.veryai.arcreactor.util.SSHValidator;
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

  private static final String PROVISIONING_POLL_TIMER = "provisioning-poll";
  private static final String CONFIRM_TIMER = "reservation-confirm";
  private static final String RESERVATION_TIMER = "resource-reservation";
  private static final String DELETION_TIMER = "instance-deletion";
  private static final String RECOVERY_TIMER = "instance-recovery";
  private static final Executor CLOUD_INIT_EXECUTOR =
      task -> Thread.ofVirtual().name("instance-cloud-init").start(task);

  private final String instanceId;
  private final ActorContext<WorkflowCommand> context;
  private final TimerScheduler<WorkflowCommand> timers;
  private final HypervisorPlacement placement;
  private final OpenStackClient openStackClient;
  private final CloudInitRenderer cloudInitRenderer;
  private final InstanceDependencyPreparer dependencyPreparer;
  private final InstanceWorkflowSettings settings;
  private final Clock clock;
  private final InstanceRecovery recovery;
  private final InstanceForwardingOperations forwardingOperations;
  private final Map<String, String> forwardingRequests = new java.util.HashMap<>();
  private String qosUpdateRequestToken;
  private final InstanceSshChecker sshChecker = new InstanceSshChecker(new SSHValidator());
  private final InstanceMetrics metrics = InstanceMetrics.NOOP;
  private String dependencyRequestToken;
  private String resourceReservationRequestToken;
  private String networkPreparationRequestToken;
  private String securityGroupRequestToken;
  private String novaCreationRequestToken;
  private String provisioningCheckRequestToken;
  private String novaDeletionRequestToken;
  private String novaDeletionStatusRequestToken;
  private String forwardingDeletionRequestToken;
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
    Objects.requireNonNull(floatingIpForwardings);
    this.dependencyPreparer = Objects.requireNonNull(dependencyPreparer);
    this.settings = Objects.requireNonNull(settings);
    this.clock = Objects.requireNonNull(clock);
    this.forwardingOperations = new InstanceForwardingOperations(openStackClient, floatingIpForwardings);
    this.recovery = new InstanceRecovery(placement, openStackClient, floatingIpForwardings,
        dependencyPreparer, sshChecker, clock);
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
        .onCommand(CreateInstanceForwarding.class, this::onCreateForwarding)
        .onCommand(DeleteInstanceForwarding.class, this::onDeleteForwarding)
        .onCommand(UpdateInstanceQos.class, this::onUpdateInstanceQos)
        .onCommand(QosUpdateCompleted.class, this::onQosUpdateCompleted)
        .onCommand(DependenciesReady.class, this::onDependenciesReady)
        .onCommand(DependenciesFailed.class, this::onDependenciesFailed)
        .onCommand(GetInstanceState.class, this::onGetState)
        .onCommand(SecurityGroupPreparationCompleted.class, this::onSecurityGroupPreparationCompleted)
        .onCommand(NetworkPreparationCompleted.class, this::onNetworkPreparationCompleted)
        .onCommand(CheckProvisioning.class, this::onCheckProvisioning)
        .onCommand(NovaCreationCompleted.class, this::onNovaCreationCompleted)
        .onCommand(ProvisioningChecked.class, this::onProvisioningChecked)
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
        .onCommand(ForwardingsDeletionCompleted.class, this::onForwardingsDeletionCompleted)
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
              || command instanceof CreateInstance || command instanceof DeleteInstance
              || (command instanceof CreateInstanceForwarding create && !create.instanceInitialization())
              || (command instanceof DeleteInstanceForwarding delete && delete.scope() == ForwardingScope.USER)
              || command instanceof UpdateInstanceQos)) {
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
    if (command.spec().forwarding() == null) {
      command.replyTo().tell(InstanceReply.rejected("Port forwarding requirements are required"));
      return Effect().none();
    }
    if (!command.spec().forwarding().internalPorts().contains(22)) {
      command.replyTo().tell(InstanceReply.rejected("SSH connectivity checking requires forwarding internal port 22"));
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

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCreateForwarding(
      InstanceWorkflowState state, CreateInstanceForwarding command) {
    InstanceForwardingSpec forwarding;
    int port = command.internalPort();
    if (command.instanceInitialization()) {
      if (state.phase() != InstancePhase.PREPARING_FORWARDING || state.pollingGeneration() != command.generation()
          || state.currentForwardingPort() != port) return Effect().none();
      InstanceForwardingSpec requirement = state.forwardingSpecs().get(port);
      if (requirement.operation() != null) return Effect().none();
      forwarding = InstanceForwardingSpec.creating(port, requirement.kind(), forwardingClaimId(state, port),
          command.instanceInitialization(), state.spec().forwarding().floatingIpIds(), null);
    } else {
      if (state.phase() != InstancePhase.ACTIVE || state.hasPendingOperation()) {
        command.replyTo().tell(InstanceMutationReply.rejected("Instance must be ACTIVE with no pending management operation"));
        return Effect().none();
      }
      if (state.forwardingSpecs().containsKey(port)) {
        command.replyTo().tell(InstanceMutationReply.rejected("The internal port already has a system or user forwarding"));
        return Effect().none();
      }
      PreparedForwarding ssh = state.preparedForwardings().get(22);
      if (ssh == null) {
        command.replyTo().tell(InstanceMutationReply.rejected("No SSH floating IP is available"));
        return Effect().none();
      }
      forwarding = InstanceForwardingSpec.creating(port, ForwardingKind.USER, UUID.randomUUID().toString(),
          command.instanceInitialization(), List.of(ssh.floatingIpId()), ssh.osFloatingIpId());
    }
    return Effect().persist(new ForwardingCreationRequested(instanceId, forwarding)).thenRun(next -> {
      if (command.replyTo() != null) command.replyTo().tell(InstanceMutationReply.accepted(forwarding.operation().claimId()));
      requestForwarding(next, forwarding, 1, true);
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onDeleteForwarding(
      InstanceWorkflowState state, DeleteInstanceForwarding command) {
    if (command.scope() == ForwardingScope.INSTANCE) {
      if (state.phase() == InstancePhase.DELETING_FORWARDINGS && state.pollingGeneration() == command.generation()) {
        requestForwardingsDeletion(state, true);
      }
      return Effect().none();
    }
    if (state.phase() != InstancePhase.ACTIVE) {
      command.replyTo().tell(InstanceMutationReply.rejected("Only ACTIVE instances allow user forwarding management"));
      return Effect().none();
    }
    if (state.preparedForwardings().values().stream().anyMatch(item ->
        item.kind() == ForwardingKind.SYSTEM && item.claimId().equals(command.forwardingId()))) {
      command.replyTo().tell(InstanceMutationReply.rejected("System forwardings cannot be deleted individually"));
      return Effect().none();
    }
    InstanceForwardingSpec forwarding = state.userForwarding(command.forwardingId());
    if (forwarding == null) {
      command.replyTo().tell(InstanceMutationReply.rejected("User forwarding was not found"));
      return Effect().none();
    }
    Phase phase = forwarding.operation().phase();
    if (phase == Phase.DELETED || phase == Phase.DELETING) {
      command.replyTo().tell(InstanceMutationReply.accepted(command.forwardingId()));
      return Effect().none();
    }
    if (state.hasPendingOperation()) {
      command.replyTo().tell(InstanceMutationReply.rejected("Wait for the pending forwarding or QoS operation"));
      return Effect().none();
    }
    InstanceForwardingSpec deleting = forwarding.transition(Phase.DELETING, null);
    return Effect().persist(new UserForwardingDeletionRequested(instanceId, deleting))
        .thenRun(next -> {
          command.replyTo().tell(InstanceMutationReply.accepted(command.forwardingId()));
          requestForwarding(next, deleting, 1, true);
        });
  }

  private void requestForwarding(InstanceWorkflowState state, InstanceForwardingSpec forwarding,
      int attempt, boolean submit) {
    pipeForwarding(forwarding, attempt, submit, () -> forwarding.operation().phase() == Phase.CREATING
        ? forwardingOperations.requestCreation(state, forwarding, submit)
        : forwardingOperations.requestDeletion(state, forwarding.deletionTarget(), submit));
  }

  private void recoverForwarding(InstanceWorkflowState state, InstanceForwardingSpec forwarding) {
    pipeForwarding(forwarding, 1, true, () -> forwarding.operation().phase() == Phase.CREATING
        ? forwardingOperations.recoverForwardingCreation(state, forwarding)
        : forwardingOperations.recoverForwardingDeletion(state, forwarding.deletionTarget()));
  }

  private void pipeForwarding(InstanceForwardingSpec forwarding, int attempt, boolean submission,
      java.util.function.Supplier<CompletionStage<ForwardingReply>> operation) {
    var claim = forwarding.operation();
    String token = UUID.randomUUID().toString();
    if (forwardingRequests.putIfAbsent(claim.claimId(), token) != null) return;
    CompletionStage<ForwardingReply> request;
    try { request = operation.get(); }
    catch (RuntimeException error) { request = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(request, (reply, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      String failureCode = null;
      if (cause instanceof InstanceForwardingOperations.InvalidForwardingResult) {
        failureCode = "FORWARDING_RESULT_INVALID";
      } else if (definitiveOperationFailure(cause)) {
        failureCode = claim.phase() == Phase.CREATING ? "SECURITY_GROUP_RULE_FAILED" : "FORWARDING_DELETE_FAILED";
      }
      return new ForwardingStatusCompleted(claim.claimId(), claim.generation(), token, attempt, submission,
          reply, failureCode, cause == null ? null : cause.toString());
    });
  }

  private static boolean definitiveOperationFailure(Throwable error) {
    if (error instanceof OpenStackOperationException || error instanceof IllegalArgumentException
        || error instanceof IllegalStateException) return true;
    return error instanceof org.openstack4j.api.exceptions.ResponseException response
        && response.getStatus() >= 400 && response.getStatus() < 500
        && response.getStatus() != 408 && response.getStatus() != 429;
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckForwardingStatus(
      InstanceWorkflowState state, CheckForwardingStatus command) {
    InstanceForwardingSpec forwarding = state.forwardingByClaimId(command.claimId());
    if (matchesForwarding(state, forwarding, command.generation())) {
      requestForwarding(state, forwarding, command.attempt(), false);
    }
    return Effect().none();
  }

  private boolean matchesForwarding(InstanceWorkflowState state, InstanceForwardingSpec forwarding, long generation) {
    if (forwarding == null || !forwarding.inProgress() || forwarding.operation().generation() != generation) return false;
    var operation = forwarding.operation();
    return state.phase() == (operation.phase() == Phase.CREATING && operation.instanceInitialization()
        ? InstancePhase.PREPARING_FORWARDING : InstancePhase.ACTIVE);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onForwardingStatusCompleted(
      InstanceWorkflowState state, ForwardingStatusCompleted command) {
    InstanceForwardingSpec forwarding = state.forwardingByClaimId(command.claimId());
    if (!matchesForwarding(state, forwarding, command.generation())
        || !forwardingRequests.remove(command.claimId(), command.requestToken())) return Effect().none();

    if (command.failureCode() != null) {
      if (forwarding.operation().phase() == Phase.DELETING) return failForwardingDeletion(forwarding, command.failure());
      return failForwardingCreation(forwarding, command.failureCode(), command.failure());
    }
    ForwardingReply reply = command.reply();
    if (reply == null) return pollForwarding(forwarding, command.attempt() + 1);
    if (forwarding.operation().phase() == Phase.DELETING) {
      return switch (reply.status()) {
        case DELETED -> Effect().persist(new UserForwardingDeleted(instanceId, forwarding.transition(Phase.DELETED, null)));
        case DELETE_FAILED, REJECTED -> failForwardingDeletion(forwarding, forwardingFailure(reply));
        default -> pollForwarding(forwarding, command.attempt() + 1);
      };
    }
    return switch (reply.status()) {
      case READY -> forwardingReady(state, forwarding, PreparedForwarding.from(reply.claim()));
      case NOT_FOUND -> {
        if (command.submission()) yield pollForwarding(forwarding, command.attempt() + 1);
        requestForwarding(state, forwarding, command.attempt(), true);
        yield Effect().none();
      }
      case IN_PROGRESS -> pollForwarding(forwarding, command.attempt() + 1);
      case FAILED, DELETE_FAILED, REJECTED, DELETED -> {
        if ((reply.claim() == null || !reply.claim().ownsPort()) && forwarding.hasNextCandidate()) {
          InstanceForwardingSpec nextCandidate = forwarding.nextCandidate();
          yield Effect().persist(new ForwardingCandidateSelected(instanceId, nextCandidate))
              .thenRun(next -> requestForwarding(next, nextCandidate, 1, true));
        }
        yield failForwardingCreation(forwarding, "FORWARDING_PREPARATION_FAILED", forwardingFailure(reply));
      }
    };
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> forwardingReady(InstanceWorkflowState state,
      InstanceForwardingSpec forwarding, PreparedForwarding resources) {
    var created = new ForwardingCreated(instanceId, forwarding.transition(Phase.READY, null), resources);
    if (!forwarding.operation().instanceInitialization()) return Effect().persist(created);
    if (state.onlyCurrentInitializationPortPending()) {
      return Effect().persist(List.of(created, novaCreationStarted(state, clock.instant()))).thenRun(this::requestNovaCreation);
    }
    return Effect().persist(created).thenRun(this::startForwardingPreparation);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> pollForwarding(InstanceForwardingSpec forwarding, int attempt) {
    var operation = forwarding.operation();
    Duration delay = operation.phase() == Phase.DELETING ? settings.pollInitialDelay() : settings.pollDelay(attempt);
    timers.startSingleTimer("forwarding:" + operation.claimId(),
        new CheckForwardingStatus(operation.claimId(), operation.generation(), attempt), delay);
    return Effect().none();
  }

  private static String forwardingFailure(ForwardingReply reply) {
    return reply.detail() == null ? reply.status().name() : reply.detail();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> failForwardingCreation(
      InstanceForwardingSpec forwarding, String code, String detail) {
    var failed = new ForwardingCreationFailed(instanceId, forwarding.transition(Phase.FAILED, detail));
    if (!forwarding.operation().instanceInitialization()) return Effect().persist(failed);
    return Effect().persist(List.of(failed, new InstanceProvisioningFailed(instanceId, code, detail, clock.instant())))
        .thenRun(next -> creationFailed());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> failForwardingDeletion(InstanceForwardingSpec forwarding, String detail) {
    return Effect().persist(new UserForwardingDeletionFailed(instanceId, forwarding.transition(Phase.DELETE_FAILED, detail)));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onUpdateInstanceQos(
      InstanceWorkflowState state, UpdateInstanceQos command) {
    if (state.phase() != InstancePhase.ACTIVE || state.hasPendingOperation()) {
      command.replyTo().tell(InstanceMutationReply.rejected("Instance must be ACTIVE with no pending management operation"));
      return Effect().none();
    }
    InstanceQosUpdate previous = state.qosUpdate();
    if (previous != null && previous.phase() == InstanceQosUpdate.Phase.FAILED
        && (!Objects.equals(previous.parentPort(), command.parentPort())
            || !Objects.equals(previous.subPort(), command.subPort()))) {
      command.replyTo().tell(InstanceMutationReply.rejected("Retry the failed QoS update with the same limits before changing them"));
      return Effect().none();
    }
    InstanceQosUpdate update = new InstanceQosUpdate(UUID.randomUUID().toString(), InstanceQosUpdate.Phase.UPDATING,
        command.parentPort(), command.subPort(), null);
    return Effect().persist(new InstanceQosUpdateRequested(instanceId, update)).thenRun(next -> {
      command.replyTo().tell(InstanceMutationReply.accepted(update.operationId()));
      requestQosUpdate(next);
    });
  }

  private void requestQosUpdate(InstanceWorkflowState state) {
    InstanceQosUpdate update = state.qosUpdate();
    pipeQosUpdate(update, () -> openStackClient.updateInstanceQos(target(state), networkOperationId(state),
        state.networkPreparation().resources(), update.parentPort(), update.subPort()));
  }

  private void pipeQosUpdate(InstanceQosUpdate update,
      java.util.function.Supplier<CompletionStage<PreparedNetwork>> operation) {
    if (qosUpdateRequestToken != null) return;
    String token = UUID.randomUUID().toString();
    qosUpdateRequestToken = token;
    CompletionStage<PreparedNetwork> request;
    try { request = operation.get(); }
    catch (RuntimeException error) { request = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(request, (resources, error) -> new QosUpdateCompleted(update.operationId(), token,
        resources, error == null ? null : rootCause(error).toString()));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onQosUpdateCompleted(
      InstanceWorkflowState state, QosUpdateCompleted command) {
    InstanceQosUpdate update = state.qosUpdate();
    if (state.phase() != InstancePhase.ACTIVE || update == null || update.phase() != InstanceQosUpdate.Phase.UPDATING
        || !update.operationId().equals(command.operationId()) || qosUpdateRequestToken == null
        || !qosUpdateRequestToken.equals(command.requestToken())) return Effect().none();
    qosUpdateRequestToken = null;
    if (command.failure() != null || command.resources() == null) {
      return Effect().persist(new InstanceQosUpdateFailed(instanceId,
          update.failed(command.failure() == null ? "QoS update returned no resources" : command.failure())));
    }
    return Effect().persist(new InstanceQosUpdated(instanceId, update.completed(), command.resources()));
  }

  private void recoverManagement(InstanceWorkflowState state) {
    for (InstanceForwardingSpec forwarding : state.forwardingSpecs().values()) {
      if (forwarding.inProgress()) recoverForwarding(state, forwarding);
    }
    InstanceQosUpdate update = state.qosUpdate();
    if (update != null && update.phase() == InstanceQosUpdate.Phase.UPDATING) {
      // Reapply only the saved intent; the client reads actual rules before repairing a partial update.
      pipeQosUpdate(update, () -> openStackClient.updateInstanceQos(target(state), networkOperationId(state),
          state.networkPreparation().resources(), update.parentPort(), update.subPort()));
    }
  }

  private void requestDependencies(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    dependencyRequestToken = token;
    var share = state.spec().share();
    CompletionStage<PreparedDependencies> request;
    try {
      request = dependencyPreparer.prepare(state.userId(), state.spec().clusterId(),
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
    CompletionStage<String> request;
    try {
      request = openStackClient.prepareInstanceSecurityGroup(target(state), instanceId);
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
            new NetworkPreparationStarted(instanceId)))
        .thenRun(this::requestNetwork);
  }

  private NovaCreationStarted novaCreationStarted(InstanceWorkflowState state, Instant now) {
    return new NovaCreationStarted(instanceId, operationId(state), now);
  }

  private void requestNetwork(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    networkPreparationRequestToken = token;
    CompletionStage<PreparedNetwork> request;
    try {
      request = openStackClient.prepareInstanceNetwork(target(state), networkOperationId(state),
          state.dependencies().network(), state.osSecurityGroupId());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (resources, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new NetworkPreparationCompleted(state.pollingGeneration(), token, resources,
          cause == null ? null : cause.toString());
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onNetworkPreparationCompleted(
      InstanceWorkflowState state, NetworkPreparationCompleted command) {
    if (state.phase() != InstancePhase.PREPARING_NETWORK || state.pollingGeneration() != command.generation()
        || networkPreparationRequestToken == null
        || !networkPreparationRequestToken.equals(command.requestToken())) return Effect().none();
    networkPreparationRequestToken = null;
    if (command.failure() != null || command.resources() == null) {
      return failCreation("NETWORK_PREPARATION_FAILED",
          command.failure() == null ? "Missing instance network preparation result" : command.failure());
    }
    return Effect().persist(List.of(new NetworkPrepared(instanceId, command.resources()),
        new ForwardingPreparationStarted(instanceId))).thenRun(this::startForwardingPreparation);
  }

  private void startForwardingPreparation(InstanceWorkflowState state) {
    context.getSelf().tell(new CreateInstanceForwarding(state.currentForwardingPort(), true, state.pollingGeneration(), null));
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
    if (novaCreationRequestToken == null && provisioningCheckRequestToken == null) requestProvisioningCheck(state);
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
    return Effect().persist(new NovaServerCreated(instanceId, state.novaOperationId(), server.osServerId(),
        server.status(), clock.instant())).thenRun(this::scheduleProvisioningPoll);
  }

  private void requestProvisioningCheck(InstanceWorkflowState state) {
    if (state.osNovaServerId() == null) {
      startNovaRecovery(state);
      return;
    }
    String token = UUID.randomUUID().toString();
    provisioningCheckRequestToken = token;
    long generation = state.pollingGeneration();
    CompletionStage<OpenStackClient.NovaServer> request;
    try {
      request = openStackClient.getServer(target(state), state.osNovaServerId());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    var check = request.thenCompose(server -> {
      if (server == null || server.status() != OpenStackClient.NovaServer.Status.ACTIVE) {
        return CompletableFuture.completedFuture(new ProvisioningChecked(generation, token, server, false, null));
      }
      CompletionStage<Boolean> ssh;
      try {
        ssh = sshChecker.check(state);
      } catch (RuntimeException error) {
        ssh = CompletableFuture.failedFuture(error);
      }
      return ssh.handle((connected, error) -> new ProvisioningChecked(generation, token, server,
          Boolean.TRUE.equals(connected), error == null ? null : rootCause(error).toString()));
    });
    context.pipeToSelf(check, (result, error) -> error == null ? result
        : new ProvisioningChecked(generation, token, null, false, rootCause(error).toString()));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onProvisioningChecked(
      InstanceWorkflowState state, ProvisioningChecked command) {
    if (state.phase() != InstancePhase.PROVISIONING || state.pollingGeneration() != command.generation()
        || provisioningCheckRequestToken == null || !provisioningCheckRequestToken.equals(command.requestToken())) return Effect().none();
    provisioningCheckRequestToken = null;
    OpenStackClient.NovaServer server = command.server();
    if (server == null) {
      if (command.failure() == null) return failCreation("NOVA_SERVER_MISSING", "The recorded Nova server no longer exists");
      scheduleProvisioningPoll(state);
      return Effect().none();
    }
    List<InstanceWorkflowEvent> events = new ArrayList<>();
    Instant now = clock.instant();
    if (!server.osServerId().equals(state.osNovaServerId()) || server.status() != state.novaStatus()) {
      events.add(new NovaServerCreated(instanceId, state.novaOperationId(), server.osServerId(), server.status(), now));
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ERROR) {
      events.add(new InstanceProvisioningFailed(instanceId, "NOVA_ERROR", server.fault(), now));
      return Effect().persist(events).thenRun(next -> creationFailed());
    }
    if (command.failure() != null) {
      events.add(new InstanceProvisioningFailed(instanceId, "SSH_CHECK_FAILED", command.failure(), now));
      return Effect().persist(events).thenRun(next -> creationFailed());
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ACTIVE && command.sshConnected()) {
      events.add(new SshConnectivityVerified(instanceId, now));
      return Effect().persist(events).thenRun(this::startConfirm);
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
    if (state.phase() != InstancePhase.FAILED && state.phase() != InstancePhase.ACTIVE) {
      command.replyTo().tell(InstanceReply.rejected("Only ACTIVE or FAILED instances can start deletion"));
      return Effect().none();
    }
    if (state.hasPendingOperation()) {
      command.replyTo().tell(InstanceReply.rejected("Wait for instance forwarding or QoS operations before deletion"));
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

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onForwardingsDeletionCompleted(
      InstanceWorkflowState state, ForwardingsDeletionCompleted command) {
    if (!matchesDeletion(state, InstancePhase.DELETING_FORWARDINGS, command.generation(),
        forwardingDeletionRequestToken, command.requestToken())) return Effect().none();
    forwardingDeletionRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    if (command.deleted()) {
      return Effect().persist(InstanceForwardingsDeleted.completionEvents(state, clock.instant()))
          .thenRun(this::startDeletionStage);
    }
    // A delete reply may only acknowledge acceptance; the next request queries the persisted claim state.
    scheduleDeletionCheck(state);
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
      case DELETING_FORWARDINGS -> requestForwardingsDeletion(state, false);
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
    networkPreparationRequestToken = null;
    novaCreationRequestToken = null;
    provisioningCheckRequestToken = null;
    forwardingRequests.clear();
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
        spec.region(), spec.hypervisorType(), spec.hostname());
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
        Map.entry("share_access_to", share.accessTo()),
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
      case DELETING_FORWARDINGS -> context.getSelf().tell(DeleteInstanceForwarding.all(state.pollingGeneration()));
      case DELETING_NETWORK -> requestInstanceNetworkDeletion(state);
      case DELETING_SECURITY_GROUP -> requestInstanceSecurityGroupDeletion(state);
      case CANCELLING_RESERVATION -> requestReservationCancellation(state);
      case RELEASING_ALLOCATION -> requestAllocationRelease(state);
      default -> throw new IllegalStateException("Cannot start deletion stage: " + state.phase());
    }
  }

  private boolean deletionInFlight() {
    return novaDeletionRequestToken != null || novaDeletionStatusRequestToken != null
        || forwardingDeletionRequestToken != null
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

  private void requestForwardingsDeletion(InstanceWorkflowState state, boolean submit) {
    if (state.phase() != InstancePhase.DELETING_FORWARDINGS || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    forwardingDeletionRequestToken = token;
    CompletionStage<Boolean> request;
    try {
      List<CompletableFuture<ForwardingReply>> deletions = new ArrayList<>();
      for (ForwardingDeletionTarget forwarding : state.forwardingsForDeletion()) {
        deletions.add(forwardingOperations.requestDeletion(state, forwarding, submit)
            .toCompletableFuture());
      }
      request = CompletableFuture.allOf(deletions.toArray(CompletableFuture[]::new))
          .thenApply(ignored -> deletions.stream().map(CompletableFuture::join)
              .allMatch(reply -> reply != null && reply.status() == ForwardingReply.Status.DELETED));
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (deleted, error) -> new ForwardingsDeletionCompleted(
        state.pollingGeneration(), token, Boolean.TRUE.equals(deleted), error == null ? null : rootCause(error).toString()));
  }

  private void requestInstanceNetworkDeletion(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.DELETING_NETWORK || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    networkDeletionRequestToken = token;
    CompletionStage<Void> request;
    try {
      request = state.networkPreparation() == null ? CompletableFuture.completedFuture(null)
          : openStackClient.deleteInstanceNetwork(target(state), networkOperationId(state),
              state.networkPreparation().resources());
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
    timers.startSingleTimer(PROVISIONING_POLL_TIMER, new CheckProvisioning(state.pollingGeneration()), settings.pollInitialDelay());
  }

  private void scheduleConfirmRetry(InstanceWorkflowState state) {
    timers.startSingleTimer(
        CONFIRM_TIMER, new RetryConfirm(state.pollingGeneration()), settings.pollInitialDelay());
  }

  private void scheduleDeletionCheck(InstanceWorkflowState state) {
    timers.startSingleTimer(DELETION_TIMER, new CheckDeletion(state.pollingGeneration()), settings.pollMaxDelay());
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
        .onEvent(ForwardingCreationRequested.class, (state, event) -> state.forwardingChanged(event.spec()))
        .onEvent(ForwardingCreated.class, (state, event) -> state.forwardingCreated(event.spec(), event.resources()))
        .onEvent(ForwardingCreationFailed.class, (state, event) -> state.forwardingChanged(event.spec()))
        .onEvent(UserForwardingDeletionRequested.class, (state, event) -> state.forwardingChanged(event.spec()))
        .onEvent(UserForwardingDeleted.class, (state, event) -> state.userForwardingDeleted(event.spec()))
        .onEvent(UserForwardingDeletionFailed.class, (state, event) -> state.forwardingChanged(event.spec()))
        .onEvent(InstanceQosUpdateRequested.class, (state, event) -> state.qosUpdateChanged(event.update()))
        .onEvent(InstanceQosUpdated.class, (state, event) -> state.qosUpdated(event.update(), event.resources()))
        .onEvent(InstanceQosUpdateFailed.class, (state, event) -> state.qosUpdateChanged(event.update()))
        .onEvent(DependencyPreparationStarted.class, (state, event) -> state.preparingDependencies())
        .onEvent(DependenciesPrepared.class, (state, event) -> state.dependenciesPrepared(event.dependencies()))
        .onEvent(SecurityGroupPreparationStarted.class, (state, event) -> state.preparingSecurityGroup())
        .onEvent(SecurityGroupPrepared.class, (state, event) -> state.securityGroupPrepared(event.osSecurityGroupId()))
        .onEvent(NetworkPreparationStarted.class, (state, event) -> state.preparingNetwork())
        .onEvent(NetworkPrepared.class, (state, event) -> state.networkPrepared(event.resources()))
        .onEvent(ForwardingPreparationStarted.class, (state, event) -> state.preparingForwarding())
        .onEvent(ForwardingCandidateSelected.class, (state, event) -> state.forwardingChanged(event.spec()))
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
        .onEvent(SshConnectivityVerified.class, (state, event) -> state.confirming())
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
    timers.cancel(PROVISIONING_POLL_TIMER);
    novaCreationRequestToken = null;
    provisioningCheckRequestToken = null;
    recoveringPhase = InstancePhase.PROVISIONING;
    scheduleRecovery(state, 1);
  }

  private void recoverState(InstanceWorkflowState state) {
    timers.cancelAll();
    forwardingRequests.clear();
    qosUpdateRequestToken = null;
    dependencyRequestToken = null;
    resourceReservationRequestToken = null;
    securityGroupRequestToken = null;
    networkPreparationRequestToken = null;
    novaCreationRequestToken = null;
    provisioningCheckRequestToken = null;
    novaDeletionRequestToken = null;
    novaDeletionStatusRequestToken = null;
    forwardingDeletionRequestToken = null;
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
          PREPARING_NETWORK,
          PROVISIONING, CONFIRMING -> state.phase();
      default -> null;
    };
    if (recoveringPhase == null) {
      if (state.phase() == InstancePhase.PREPARING_FORWARDING) {
        InstanceForwardingSpec pending = state.forwardingSpecs().get(state.currentForwardingPort());
        if (pending.operation() == null) startForwardingPreparation(state);
        else recoverForwarding(state, pending);
      } else if (state.phase() == InstancePhase.ACTIVE) {
        recoverManagement(state);
      }
      return;
    }
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
    Duration delay = state.phase().isDeleting() ? settings.pollMaxDelay() : settings.pollInitialDelay();
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
      case PREPARING_NETWORK -> requestNetwork(state);
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
