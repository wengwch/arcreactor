package cn.veryai.arcreactor.actor.instance;

import static cn.veryai.arcreactor.actor.instance.InstanceOperationIds.*;

import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import cn.veryai.arcreactor.actor.instance.command.CreateInstanceForwarding;
import cn.veryai.arcreactor.actor.instance.command.DeleteInstanceForwarding;
import cn.veryai.arcreactor.actor.instance.command.UpdateInstanceQos;
import cn.veryai.arcreactor.actor.instance.command.RebootInstance;
import cn.veryai.arcreactor.actor.instance.command.RebuildInstance;
import cn.veryai.arcreactor.actor.instance.command.CheckServerOperation;
import cn.veryai.arcreactor.actor.instance.command.ServerOperationSubmitted;
import cn.veryai.arcreactor.actor.instance.command.ServerOperationChecked;
import cn.veryai.arcreactor.actor.instance.command.PrepareInstanceForwardings;
import cn.veryai.arcreactor.actor.instance.command.DeleteInstanceForwardings;
import cn.veryai.arcreactor.actor.instance.command.InstanceForwardingsChanged;
import cn.veryai.arcreactor.actor.instance.command.QosUpdateCompleted;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState.Phase;
import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import cn.veryai.arcreactor.actor.instance.event.InstanceForwardingEvent;
import cn.veryai.arcreactor.actor.instance.event.ForwardingDeleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdated;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationSubmitted;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationCompleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationFailed;
import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.instance.command.CheckDeletion;
import cn.veryai.arcreactor.actor.instance.command.CheckForwardingStatus;
import cn.veryai.arcreactor.actor.instance.command.CheckProvisioning;
import cn.veryai.arcreactor.actor.instance.command.ConfirmCompleted;
import cn.veryai.arcreactor.actor.instance.command.CreateInstance;
import cn.veryai.arcreactor.actor.instance.command.DeleteInstance;
import cn.veryai.arcreactor.actor.instance.command.AllocationReleaseCompleted;
import cn.veryai.arcreactor.actor.instance.command.InstanceNetworkDeletionCompleted;
import cn.veryai.arcreactor.actor.instance.command.InstanceSecurityGroupDeletionCompleted;
import cn.veryai.arcreactor.actor.instance.command.ReservationCancellationCompleted;
import cn.veryai.arcreactor.actor.instance.command.ResourceReservationCompleted;
import cn.veryai.arcreactor.actor.instance.command.RetryResourceReservation;
import cn.veryai.arcreactor.actor.instance.command.DependencyPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.ForwardingStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.GetInstanceState;
import cn.veryai.arcreactor.actor.instance.command.NetworkPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.NovaCreationCompleted;
import cn.veryai.arcreactor.actor.instance.command.NovaDeletionChecked;
import cn.veryai.arcreactor.actor.instance.command.NovaDeletionSubmitted;
import cn.veryai.arcreactor.actor.instance.command.ProvisioningChecked;
import cn.veryai.arcreactor.actor.instance.command.RetryConfirm;
import cn.veryai.arcreactor.actor.instance.command.SchedulingCompleted;
import cn.veryai.arcreactor.actor.instance.command.SecurityGroupPreparationCompleted;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.DependenciesPrepared;
import cn.veryai.arcreactor.actor.instance.event.DependencyPreparationStarted;
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
import cn.veryai.arcreactor.actor.instance.event.InstanceDeletionFailed;
import cn.veryai.arcreactor.actor.instance.event.ForwardingDeletionFailed;
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
import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.actor.instance.model.PreparedShare;
import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import cn.veryai.arcreactor.exceptions.BaseException;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import cn.veryai.arcreactor.util.SSHValidator;
import java.time.Clock;
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
import java.util.function.Supplier;
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

/** Coordinates instance creation, management operations and externally requested deletion. */
public final class InstanceWorkflowActor
    extends EventSourcedBehavior<WorkflowCommand, InstanceWorkflowEvent, InstanceWorkflowState> {
  public static final String ENTITY_TYPE = "InstanceWorkflow";
  public static final EntityTypeKey<WorkflowCommand> TYPE_KEY =
      EntityTypeKey.create(WorkflowCommand.class, ENTITY_TYPE);

  private static final String PROVISIONING_POLL_TIMER = "provisioning-poll";
  private static final String CONFIRM_TIMER = "reservation-confirm";
  private static final String RESERVATION_TIMER = "resource-reservation";
  private static final String DELETION_TIMER = "instance-deletion";
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
  private final InstanceForwardingWorkflow forwardings;
  private final InstanceServerOperationWorkflow serverOperations;
  private String qosUpdateRequestToken;
  private final InstanceSshChecker sshChecker = new InstanceSshChecker(new SSHValidator());
  private String schedulingRequestToken;
  private String dependencyRequestToken;
  private String resourceReservationRequestToken;
  private String networkPreparationRequestToken;
  private String securityGroupRequestToken;
  private String novaCreationRequestToken;
  private String provisioningCheckRequestToken;
  private String confirmationRequestToken;
  private String deletionRequestToken;
  private long deletionPollAttempt;

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
    this.forwardings = new InstanceForwardingWorkflow(instanceId, context, timers,
        openStackClient, floatingIpForwardings, settings, Effect());
    this.serverOperations = new InstanceServerOperationWorkflow(instanceId, context, timers,
        openStackClient, sshChecker, settings, clock, Effect());
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
        .onCommand(DeleteInstance.class, this::onDelete)
        .onCommand(CreateInstanceForwarding.class, forwardings::onCreate)
        .onCommand(DeleteInstanceForwarding.class, forwardings::onDelete)
        .onCommand(PrepareInstanceForwardings.class, this::onPrepareInstanceForwardings)
        .onCommand(DeleteInstanceForwardings.class, this::onDeleteInstanceForwardings)
        .onCommand(InstanceForwardingsChanged.class, this::onInstanceForwardingsChanged)
        .onCommand(UpdateInstanceQos.class, this::onUpdateInstanceQos)
        .onCommand(QosUpdateCompleted.class, this::onQosUpdateCompleted)
        .onCommand(RebootInstance.class, serverOperations::onReboot)
        .onCommand(RebuildInstance.class, serverOperations::onRebuild)
        .onCommand(ServerOperationSubmitted.class, serverOperations::onSubmitted)
        .onCommand(CheckServerOperation.class, serverOperations::onCheck)
        .onCommand(ServerOperationChecked.class, serverOperations::onChecked)
        .onCommand(DependencyPreparationCompleted.class, this::onDependencyPreparationCompleted)
        .onCommand(GetInstanceState.class, this::onGetState)
        .onCommand(SecurityGroupPreparationCompleted.class, this::onSecurityGroupPreparationCompleted)
        .onCommand(NetworkPreparationCompleted.class, this::onNetworkPreparationCompleted)
        .onCommand(CheckProvisioning.class, this::onCheckProvisioning)
        .onCommand(NovaCreationCompleted.class, this::onNovaCreationCompleted)
        .onCommand(ProvisioningChecked.class, this::onProvisioningChecked)
        .onCommand(CheckForwardingStatus.class, forwardings::onCheck)
        .onCommand(ForwardingStatusCompleted.class, forwardings::onCompleted)
        .onCommand(SchedulingCompleted.class, this::onSchedulingCompleted)
        .onCommand(ResourceReservationCompleted.class, this::onResourceReservationCompleted)
        .onCommand(RetryResourceReservation.class, this::onRetryResourceReservation)
        .onCommand(ConfirmCompleted.class, this::onConfirmCompleted)
        .onCommand(RetryConfirm.class, this::onRetryConfirm)
        .onCommand(NovaDeletionChecked.class, this::onNovaDeletionChecked)
        .onCommand(NovaDeletionSubmitted.class, this::onNovaDeletionSubmitted)
        .onCommand(InstanceNetworkDeletionCompleted.class, this::onInstanceNetworkDeletionCompleted)
        .onCommand(InstanceSecurityGroupDeletionCompleted.class, this::onInstanceSecurityGroupDeletionCompleted)
        .onCommand(ReservationCancellationCompleted.class, this::onReservationCancellationCompleted)
        .onCommand(AllocationReleaseCompleted.class, this::onAllocationReleaseCompleted)
        .onCommand(CheckDeletion.class, this::onCheckDeletion)
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
            new SchedulingStarted(instanceId, command.requestId(), instanceId, now)))
        .thenRun(
            next -> {
              command.replyTo().tell(InstanceReply.accepted(false));
              startScheduling(next);
            });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onPrepareInstanceForwardings(
      InstanceWorkflowState state, PrepareInstanceForwardings command) {
    if (state.phase() != InstancePhase.PREPARING_FORWARDING
        || state.pollingGeneration() != command.generation()) return Effect().none();
    return prepareInstanceForwardings(state);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> prepareInstanceForwardings(InstanceWorkflowState state) {
    for (int port : state.spec().forwarding().internalPorts()) {
      InstanceForwardingState forwarding = state.forwardingByInternalPort(port);
      if (forwarding == null) {
        return forwardings.create(InstanceForwardingState.creating(port, ForwardingKind.SYSTEM,
            UUID.randomUUID().toString(), state.spec().forwarding().floatingIpIds()));
      }
      if (forwarding.phase() == Phase.FAILED) {
        return failCreation(Objects.requireNonNullElse(forwarding.failureCode(), "FORWARDING_PREPARATION_FAILED"),
            forwarding.failure());
      }
      if (forwarding.phase() != Phase.READY) return Effect().none();
    }
    return Effect().persist(novaCreationStarted(state, clock.instant())).thenRun(this::requestNovaCreation);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onDeleteInstanceForwardings(
      InstanceWorkflowState state, DeleteInstanceForwardings command) {
    if (state.phase() != InstancePhase.DELETING_FORWARDINGS
        || state.pollingGeneration() != command.generation()) return Effect().none();
    if (state.forwardingStates().isEmpty()) return completeForwardingCleanup();
    return forwardings.delete(state.forwardingStates().values().stream()
        .sorted(java.util.Comparator.comparing(InstanceForwardingState::claimId)).toList());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onInstanceForwardingsChanged(
      InstanceWorkflowState state, InstanceForwardingsChanged command) {
    if (state.pollingGeneration() != command.generation()) return Effect().none();
    if (state.phase() == InstancePhase.PREPARING_FORWARDING) return prepareInstanceForwardings(state);
    if (state.phase() == InstancePhase.DELETING_FORWARDINGS) {
      if (state.forwardingStates().isEmpty()) return completeForwardingCleanup();
      state.forwardingStates().values().stream()
          .filter(forwarding -> forwarding.phase() == Phase.DELETE_FAILED).findFirst()
          .ifPresent(forwarding -> scheduleDeletionRetry(state, forwarding.failure()));
    }
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> completeForwardingCleanup() {
    return Effect().persist(new InstanceForwardingsDeleted(instanceId, clock.instant()))
        .thenRun(this::startDeletionStage);
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
    if (qosUpdateRequestToken != null) return;
    InstanceQosUpdate update = state.qosUpdate();
    String token = UUID.randomUUID().toString();
    qosUpdateRequestToken = token;
    var request = invoke(() -> openStackClient.updateInstanceQos(target(state), networkOperationId(state),
        state.networkPreparation().resources(), update.parentPort(), update.subPort()));
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
    forwardings.recover(state);
    InstanceQosUpdate update = state.qosUpdate();
    if (update != null && update.phase() == InstanceQosUpdate.Phase.UPDATING) {
      // Reapply only the saved intent; the client reads actual rules before repairing a partial update.
      requestQosUpdate(state);
    }
  }

  private void requestDependencies(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    dependencyRequestToken = token;
    var share = state.spec().share();
    var request = invoke(() -> dependencyPreparer.prepare(state.userId(), state.spec().clusterId(),
        share.spec(), state.spec().network()));
    context.pipeToSelf(request, (dependencies, error) -> {
      if (error == null) return new DependencyPreparationCompleted(
          state.pollingGeneration(), token, dependencies, null, null);
      Throwable cause = rootCause(error);
      String code = cause instanceof BaseException business
          ? Integer.toString(business.getCode()) : "DEPENDENCY_PREPARATION_FAILED";
      return new DependencyPreparationCompleted(state.pollingGeneration(), token, null, code,
          "Dependency preparation failed: " + code + " (" + cause.getClass().getSimpleName() + ")");
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onDependencyPreparationCompleted(
      InstanceWorkflowState state, DependencyPreparationCompleted command) {
    if (state.phase() != InstancePhase.PREPARING_DEPENDENCIES || state.pollingGeneration() != command.generation()
        || dependencyRequestToken == null || !dependencyRequestToken.equals(command.requestToken())) return Effect().none();
    dependencyRequestToken = null;
    if (command.failure() != null || command.dependencies() == null) {
      return failCreation(command.failureCode() == null ? "DEPENDENCY_PREPARATION_FAILED" : command.failureCode(),
          command.failure() == null ? "Missing instance dependency preparation result" : command.failure());
    }
    return Effect().persist(List.of(
            new DependenciesPrepared(instanceId, command.dependencies()),
            new SecurityGroupPreparationStarted(instanceId)))
        .thenRun(this::startSecurityGroupPreparation);
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
    var request = invoke(() -> openStackClient.prepareInstanceSecurityGroup(target(state), instanceId));
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
    var request = invoke(() -> openStackClient.prepareInstanceNetwork(target(state), instanceId,
        state.dependencies().network(), state.osSecurityGroupId()));
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
    context.getSelf().tell(new PrepareInstanceForwardings(state.pollingGeneration()));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSchedulingCompleted(
      InstanceWorkflowState state, SchedulingCompleted command) {
    if (state.phase() != InstancePhase.SCHEDULING || state.pollingGeneration() != command.generation()
        || schedulingRequestToken == null || !schedulingRequestToken.equals(command.requestToken())) return Effect().none();
    schedulingRequestToken = null;
    if (command.failure() != null || command.result() == null) {
      return failCreation("SCHEDULING_FAILED",
          command.failure() == null ? "Missing scheduling result" : command.failure());
    }
    HypervisorPlacement.ScheduleResult result = command.result();
    if (result.hypervisorIds().isEmpty()) {
      return failCreation("NO_VALID_HOST", result.detail());
    }
    return Effect().persist(reservationStarted(result.hypervisorIds()))
        .thenRun(this::requestResourceReservation);
  }

  private HypervisorReservationStarted reservationStarted(List<String> candidates) {
    return new HypervisorReservationStarted(instanceId, candidates.getFirst(),
        candidates.subList(1, candidates.size()), clock.instant());
  }

  private void requestResourceReservation(InstanceWorkflowState state) {
    requestResourceReservation(state, 0);
  }

  private void requestResourceReservation(InstanceWorkflowState state, int retryCount) {
    if (state.phase() != InstancePhase.RESERVING_RESOURCES || resourceReservationRequestToken != null) return;
    String token = UUID.randomUUID().toString();
    resourceReservationRequestToken = token;
    // The target host and reservation ID are already durable before this side effect starts.
    var request = invoke(() -> placement.reserve(state.hypervisorId(), schedulingRequest(state)));
    context.pipeToSelf(request, (result, error) -> new ResourceReservationCompleted(
        state.pollingGeneration(), token, retryCount, error == null && result != null ? result
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
              new HypervisorSelected(instanceId, state.hypervisorId(), clock.instant()),
              new DependencyPreparationStarted(instanceId)))
          .thenRun(next -> {
            timers.cancel(RESERVATION_TIMER);
            requestDependencies(next);
          });
      case UNAVAILABLE -> {
        if (state.remainingHypervisorIds().isEmpty()) {
          yield failCreation("NO_VALID_HOST", command.result().detail());
        }
        yield Effect().persist(reservationStarted(state.remainingHypervisorIds()))
            .thenRun(next -> {
              timers.cancel(RESERVATION_TIMER);
              requestResourceReservation(next);
            });
      }
      case REJECTED -> failCreation("RESERVATION_REJECTED", command.result().detail());
      case UNKNOWN -> {
        context.getLog().warn("Resource reservation outcome unknown: instanceId={} hypervisorId={} reservationId={} reason={}",
            instanceId, state.hypervisorId(), state.reservationId(), command.result().detail());
        if (command.retryCount() >= settings.maxResourceReservationRetries()) {
          yield failCreation("RESERVATION_RETRY_EXHAUSTED",
              "Resource reservation outcome remains unknown after " + settings.maxResourceReservationRetries()
                  + " retries: " + command.result().detail());
        }
        timers.startSingleTimer(RESERVATION_TIMER,
            new RetryResourceReservation(state.pollingGeneration(), command.retryCount() + 1),
            settings.pollInitialDelay());
        yield Effect().none();
      }
    };
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onRetryResourceReservation(
      InstanceWorkflowState state, RetryResourceReservation command) {
    if (state.phase() == InstancePhase.RESERVING_RESOURCES && state.pollingGeneration() == command.generation()) {
      requestResourceReservation(state, command.retryCount());
    }
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckProvisioning(
      InstanceWorkflowState state, CheckProvisioning command) {
    if (state.phase() != InstancePhase.PROVISIONING || state.pollingGeneration() != command.generation()) return Effect().none();
    if (novaCreationRequestToken == null && provisioningCheckRequestToken == null) requestProvisioningCheck(state, command.attempt());
    else scheduleProvisioningPoll(state, command.attempt());
    return Effect().none();
  }

  private void requestNovaCreation(InstanceWorkflowState state) {
    String token = UUID.randomUUID().toString();
    novaCreationRequestToken = token;
    var request = invoke(() -> submitNova(state));
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
        server.status(), clock.instant())).thenRun(next -> scheduleProvisioningPoll(next, 1));
  }

  private void requestProvisioningCheck(InstanceWorkflowState state, int attempt) {
    String token = UUID.randomUUID().toString();
    provisioningCheckRequestToken = token;
    long generation = state.pollingGeneration();
    // An uncertain submission is resolved by lookup, never by another Nova creation request.
    var request = invoke(() -> state.osNovaServerId() == null
        ? openStackClient.findServerByOperationId(target(state), state.novaOperationId())
            .thenApply(OpenStackClient.ServerLookupResult::server)
        : openStackClient.getServer(target(state), state.osNovaServerId()));
    var check = request.thenCompose(server -> {
      if (server == null || server.status() != OpenStackClient.NovaServer.Status.ACTIVE) {
        return CompletableFuture.completedFuture(new ProvisioningChecked(generation, token, attempt, server, false, false, null));
      }
      return invoke(() -> sshChecker.check(state)).handle((connected, error) -> new ProvisioningChecked(generation, token, attempt, server,
          Boolean.TRUE.equals(connected), false, error == null ? null : rootCause(error).toString()));
    });
    context.pipeToSelf(check, (result, error) -> {
      if (error == null) return result;
      Throwable cause = rootCause(error);
      return new ProvisioningChecked(generation, token, attempt, null, false, definitiveFailure(cause), cause.toString());
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onProvisioningChecked(
      InstanceWorkflowState state, ProvisioningChecked command) {
    if (state.phase() != InstancePhase.PROVISIONING || state.pollingGeneration() != command.generation()
        || provisioningCheckRequestToken == null || !provisioningCheckRequestToken.equals(command.requestToken())) return Effect().none();
    provisioningCheckRequestToken = null;
    OpenStackClient.NovaServer server = command.server();
    List<InstanceWorkflowEvent> events = new ArrayList<>();
    Instant now = clock.instant();
    if (server == null) {
      if (command.rejected()) return failCreation("NOVA_QUERY_FAILED", command.failure());
      if (command.failure() == null) {
        return state.osNovaServerId() == null
            ? failCreation("NOVA_CREATION_UNCONFIRMED",
                "Nova creation outcome is unconfirmed; verify OpenStack manually before deleting retained resources. "
                    + "The server creation request will not be resubmitted")
            : failCreation("NOVA_SERVER_MISSING", "The recorded Nova server no longer exists");
      }
    } else {
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
    }
    if (command.attempt() >= settings.maxProvisioningPollAttempts()) {
      events.add(new InstanceProvisioningFailed(instanceId, "PROVISIONING_POLL_EXHAUSTED",
          "Instance provisioning did not become ready after " + settings.maxProvisioningPollAttempts()
              + " polling attempts" + (command.failure() == null ? "" : ": " + command.failure()), now));
      return Effect().persist(events).thenRun(next -> creationFailed());
    }
    if (events.isEmpty()) {
      scheduleProvisioningPoll(state, command.attempt() + 1);
      return Effect().none();
    }
    return Effect().persist(events).thenRun(next -> scheduleProvisioningPoll(next, command.attempt() + 1));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onConfirmCompleted(
      InstanceWorkflowState state, ConfirmCompleted command) {
    if (state.phase() != InstancePhase.CONFIRMING || state.pollingGeneration() != command.generation()
        || confirmationRequestToken == null || !confirmationRequestToken.equals(command.requestToken())) return Effect().none();
    confirmationRequestToken = null;
    return switch (command.result().status()) {
      case SUCCEEDED -> {
        Instant now = clock.instant();
        yield Effect().persist(List.of(
            new ReservationConfirmedForInstance(instanceId, state.hypervisorId(), state.reservationId(), now),
            new InstanceActivated(instanceId, state.osNovaServerId(), state.hypervisorId(), now)));
      }
      case REJECTED -> failCreation("RESERVATION_CONFIRM_REJECTED", command.result().detail());
      case UNKNOWN -> {
        if (command.retryCount() >= settings.maxConfirmationRetries()) {
          yield failCreation("RESERVATION_CONFIRM_RETRY_EXHAUSTED",
              "Reservation confirmation outcome remains unknown after " + settings.maxConfirmationRetries()
                  + " retries: " + command.result().detail());
        }
        scheduleConfirmRetry(state, command.retryCount() + 1);
        yield Effect().none();
      }
    };
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onRetryConfirm(
      InstanceWorkflowState state, RetryConfirm command) {
    if (state.phase() == InstancePhase.CONFIRMING
        && state.pollingGeneration() == command.generation()) {
      startConfirm(state, command.retryCount());
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
      command.replyTo().tell(InstanceReply.rejected("Wait for pending instance management operations before deletion"));
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
        command.requestToken())) return Effect().none();
    deletionRequestToken = null;
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
        command.requestToken())) return Effect().none();
    deletionRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    return Effect().persist(new NovaDeletionAccepted(instanceId, command.osServerId(), clock.instant()))
        .thenRun(this::scheduleDeletionCheck);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onInstanceNetworkDeletionCompleted(
      InstanceWorkflowState state, InstanceNetworkDeletionCompleted command) {
    if (!matchesDeletion(state, InstancePhase.DELETING_NETWORK, command.generation(),
        command.requestToken())) return Effect().none();
    deletionRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    return Effect().persist(new InstanceNetworkDeleted(instanceId, clock.instant()))
        .thenRun(this::startDeletionStage);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onInstanceSecurityGroupDeletionCompleted(
      InstanceWorkflowState state, InstanceSecurityGroupDeletionCompleted command) {
    if (!matchesDeletion(state, InstancePhase.DELETING_SECURITY_GROUP, command.generation(),
        command.requestToken())) return Effect().none();
    deletionRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    return Effect().persist(new InstanceSecurityGroupDeleted(instanceId, clock.instant()))
        .thenRun(this::startDeletionStage);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onReservationCancellationCompleted(
      InstanceWorkflowState state, ReservationCancellationCompleted command) {
    if (!matchesDeletion(state, InstancePhase.CANCELLING_RESERVATION, command.generation(),
        command.requestToken())) return Effect().none();
    deletionRequestToken = null;
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
        command.requestToken())) return Effect().none();
    deletionRequestToken = null;
    if (command.failure() != null) return retryDeletionStage(state, command.failure());
    if (command.result() == null || command.result().status() != HypervisorPlacement.ReservationResult.Status.SUCCEEDED) {
      return retryDeletionStage(state, command.result() == null ? "Missing release result" : command.result().detail());
    }
    return Effect().persist(new InstanceDeleted(instanceId, clock.instant()))
        .thenRun(next -> timers.cancel(DELETION_TIMER));
  }

  private boolean matchesDeletion(InstanceWorkflowState state, InstancePhase phase, long generation,
      String token) {
    return state.phase() == phase && state.pollingGeneration() == generation
        && deletionRequestToken != null && deletionRequestToken.equals(token);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> retryDeletionStage(
      InstanceWorkflowState state, String reason) {
    scheduleDeletionRetry(state, reason);
    return Effect().none();
  }

  private void scheduleDeletionRetry(InstanceWorkflowState state, String reason) {
    context.getLog().warn("Instance deletion incomplete: instanceId={} phase={} reason={}", instanceId, state.phase(), reason);
    scheduleDeletionCheck(state);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheckDeletion(
      InstanceWorkflowState state, CheckDeletion command) {
    if (!state.phase().isDeleting() || state.pollingGeneration() != command.generation()
        || command.attempt() != deletionPollAttempt + 1) return Effect().none();
    if (deletionRequestToken != null) {
      timers.startSingleTimer(DELETION_TIMER, command, settings.pollMaxDelay());
      return Effect().none();
    }
    boolean deletingForwardings = state.phase() == InstancePhase.DELETING_FORWARDINGS;
    if (deletingForwardings && state.forwardingStates().isEmpty()) return completeForwardingCleanup();
    if (command.attempt() > settings.maxDeletionPollAttempts()
        || (deletingForwardings && command.attempt() == settings.maxDeletionPollAttempts())) {
      return failDeletion(state);
    }
    deletionPollAttempt = command.attempt();
    switch (state.phase()) {
      case DELETING_NOVA -> requestNovaDeletionStatus(state);
      default -> startDeletionStage(state, command.attempt());
    }
    return Effect().none();
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> failDeletion(InstanceWorkflowState state) {
    String message = "Instance deletion did not complete in " + state.phase() + " after "
        + settings.maxDeletionPollAttempts() + " polling attempts";
    List<InstanceWorkflowEvent> events = new ArrayList<>();
    state.forwardingStates().values().stream()
        .filter(forwarding -> forwarding.phase() == Phase.DELETING)
        .sorted(java.util.Comparator.comparing(InstanceForwardingState::claimId))
        .forEach(forwarding -> events.add(new ForwardingDeletionFailed(instanceId,
            forwarding.transition(Phase.DELETE_FAILED, message))));
    events.add(new InstanceDeletionFailed(instanceId, "DELETION_POLL_EXHAUSTED", message, clock.instant()));
    return Effect().persist(events).thenRun(next -> {
      timers.cancelAll();
      deletionRequestToken = null;
      deletionPollAttempt = 0;
      forwardings.reset();
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> failCreation(
      String code, String message) {
    return Effect().persist(new InstanceProvisioningFailed(instanceId, code, message, clock.instant()))
        .thenRun(next -> creationFailed());
  }

  private void creationFailed() {
    timers.cancelAll();
    schedulingRequestToken = null;
    dependencyRequestToken = null;
    resourceReservationRequestToken = null;
    securityGroupRequestToken = null;
    networkPreparationRequestToken = null;
    novaCreationRequestToken = null;
    provisioningCheckRequestToken = null;
    confirmationRequestToken = null;
    forwardings.reset();
  }

  private void startScheduling(InstanceWorkflowState state) {
    if (schedulingRequestToken != null) return;
    String token = UUID.randomUUID().toString();
    schedulingRequestToken = token;
    context.pipeToSelf(
        invoke(() -> placement.schedule(schedulingRequest(state))),
        (result, error) -> new SchedulingCompleted(state.pollingGeneration(), token, result,
            error == null ? null : rootCause(error).toString()));
  }

  private HypervisorPlacement.ScheduleRequest schedulingRequest(InstanceWorkflowState state) {
    InstanceSpec spec = state.spec();
    return new HypervisorPlacement.ScheduleRequest(state.requestId(), state.reservationId(), instanceId,
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
    startConfirm(state, 0);
  }

  private void startConfirm(InstanceWorkflowState state, int retryCount) {
    if (confirmationRequestToken != null) return;
    String token = UUID.randomUUID().toString();
    confirmationRequestToken = token;
    context.pipeToSelf(
        invoke(() -> placement.confirm(state.hypervisorId(), state.reservationId(), instanceId)),
        (result, error) -> new ConfirmCompleted(state.pollingGeneration(), token, retryCount,
            error == null && result != null ? result : HypervisorPlacement.ReservationResult.unknown(
                error == null ? "Missing confirmation result" : rootCause(error).toString())));
  }

  private void startDeletionStage(InstanceWorkflowState state) {
    timers.cancel(DELETION_TIMER);
    startDeletionStage(state, 0);
  }

  private void startDeletionStage(InstanceWorkflowState state, long attempt) {
    deletionPollAttempt = attempt;
    switch (state.phase()) {
      case DELETING_NOVA -> {
        if (state.osNovaServerId() != null && !state.novaDeletionAccepted()) {
          requestNovaDeletion(state, state.osNovaServerId());
        } else {
          requestNovaDeletionStatus(state);
        }
      }
      case DELETING_FORWARDINGS -> {
        context.getSelf().tell(new DeleteInstanceForwardings(state.pollingGeneration()));
        scheduleDeletionCheck(state);
      }
      case DELETING_NETWORK -> requestInstanceNetworkDeletion(state);
      case DELETING_SECURITY_GROUP -> requestInstanceSecurityGroupDeletion(state);
      case CANCELLING_RESERVATION -> requestReservationCancellation(state);
      case RELEASING_ALLOCATION -> requestAllocationRelease(state);
      default -> throw new IllegalStateException("Cannot start deletion stage: " + state.phase());
    }
  }

  private boolean deletionInFlight() {
    return deletionRequestToken != null || forwardings.inFlight();
  }

  private void requestNovaDeletionStatus(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.DELETING_NOVA || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    deletionRequestToken = token;
    CompletionStage<OpenStackClient.NovaServer> request = invoke(() -> {
      if (state.osNovaServerId() != null) {
        return openStackClient.getServer(target(state), state.osNovaServerId());
      }
      if (state.novaOperationId() != null) {
        return openStackClient.findServerByOperationId(target(state), state.novaOperationId())
            .thenApply(OpenStackClient.ServerLookupResult::server);
      }
      return CompletableFuture.completedFuture(null);
    });
    context.pipeToSelf(request, (server, error) -> new NovaDeletionChecked(
        state.pollingGeneration(), token, server == null ? null : server.osServerId(),
        error == null ? null : rootCause(error).toString()));
  }

  private void requestNovaDeletion(InstanceWorkflowState state, String osServerId) {
    if (state.phase() != InstancePhase.DELETING_NOVA || state.novaDeletionAccepted() || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    deletionRequestToken = token;
    var request = invoke(() -> openStackClient.deleteServer(target(state), osServerId));
    context.pipeToSelf(request, (ignored, error) -> new NovaDeletionSubmitted(
        state.pollingGeneration(), token, osServerId, error == null ? null : rootCause(error).toString()));
  }

  private void requestInstanceNetworkDeletion(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.DELETING_NETWORK || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    deletionRequestToken = token;
    var request = invoke(() -> state.networkPreparation() == null ? CompletableFuture.completedFuture(null)
        : openStackClient.deleteInstanceNetwork(target(state), networkOperationId(state),
            state.networkPreparation().resources()));
    context.pipeToSelf(request, (ignored, error) -> new InstanceNetworkDeletionCompleted(
        state.pollingGeneration(), token, error == null ? null : rootCause(error).toString()));
  }

  private void requestInstanceSecurityGroupDeletion(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.DELETING_SECURITY_GROUP || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    deletionRequestToken = token;
    var request = invoke(() -> state.securityGroupPreparation() == null ? CompletableFuture.completedFuture(null)
        : openStackClient.deleteInstanceSecurityGroup(target(state), instanceId));
    context.pipeToSelf(request, (ignored, error) -> new InstanceSecurityGroupDeletionCompleted(
        state.pollingGeneration(), token, error == null ? null : rootCause(error).toString()));
  }

  private void requestReservationCancellation(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.CANCELLING_RESERVATION || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    deletionRequestToken = token;
    var request = invoke(() -> state.hypervisorId() == null || state.reservationId() == null
        ? CompletableFuture.completedFuture(HypervisorPlacement.ReservationResult.succeeded(true, "no reservation"))
        : placement.cancel(state.hypervisorId(), state.reservationId()));
    context.pipeToSelf(request, (result, error) -> new ReservationCancellationCompleted(
        state.pollingGeneration(), token, result, error == null ? null : rootCause(error).toString()));
  }

  private void requestAllocationRelease(InstanceWorkflowState state) {
    if (state.phase() != InstancePhase.RELEASING_ALLOCATION || deletionInFlight()) return;
    String token = UUID.randomUUID().toString();
    deletionRequestToken = token;
    var request = invoke(() -> state.hypervisorId() == null
        ? CompletableFuture.completedFuture(HypervisorPlacement.ReservationResult.succeeded(true, "no allocation"))
        : placement.release(state.hypervisorId(), instanceId));
    context.pipeToSelf(request, (result, error) -> new AllocationReleaseCompleted(
        state.pollingGeneration(), token, result, error == null ? null : rootCause(error).toString()));
  }

  private void scheduleProvisioningPoll(InstanceWorkflowState state, int attempt) {
    timers.startSingleTimer(PROVISIONING_POLL_TIMER, new CheckProvisioning(state.pollingGeneration(), attempt), settings.pollInitialDelay());
  }

  private void scheduleConfirmRetry(InstanceWorkflowState state, int retryCount) {
    timers.startSingleTimer(
        CONFIRM_TIMER, new RetryConfirm(state.pollingGeneration(), retryCount), settings.pollInitialDelay());
  }

  private void scheduleDeletionCheck(InstanceWorkflowState state) {
    if (state.phase() == InstancePhase.DELETING_FORWARDINGS && timers.isTimerActive(DELETION_TIMER)) return;
    CheckDeletion command = new CheckDeletion(state.pollingGeneration(), deletionPollAttempt + 1);
    if (deletionPollAttempt >= settings.maxDeletionPollAttempts()) {
      // The final request completed without advancing; fail without another remote call or timer delay.
      context.getSelf().tell(command);
    } else {
      timers.startSingleTimer(DELETION_TIMER, command, settings.pollMaxDelay());
    }
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
        .onEvent(InstanceForwardingEvent.class, (state, event) -> event instanceof ForwardingDeleted
            ? state.forwardingDeleted(event.forwarding()) : state.forwardingChanged(event.forwarding()))
        .onEvent(InstanceQosUpdateRequested.class, (state, event) -> state.qosUpdateChanged(event.update()))
        .onEvent(InstanceQosUpdated.class, (state, event) -> state.qosUpdated(event.update(), event.resources()))
        .onEvent(InstanceQosUpdateFailed.class, (state, event) -> state.qosUpdateChanged(event.update()))
        .onEvent(InstanceServerOperationRequested.class,
            (state, event) -> state.serverOperationChanged(event.operation(), null, null, null))
        .onEvent(InstanceServerOperationSubmitted.class,
            (state, event) -> state.serverOperationChanged(event.operation(), null, null, null))
        .onEvent(InstanceServerOperationCompleted.class,
            (state, event) -> state.serverOperationChanged(event.operation(), OpenStackClient.NovaServer.Status.ACTIVE,
                event.osImageId(), null))
        .onEvent(InstanceServerOperationFailed.class,
            (state, event) -> state.serverOperationChanged(event.operation(), event.novaStatus(), null, event.failureCode()))
        .onEvent(DependencyPreparationStarted.class, (state, event) -> state.preparingDependencies())
        .onEvent(DependenciesPrepared.class, (state, event) -> state.dependenciesPrepared(event.dependencies()))
        .onEvent(SecurityGroupPreparationStarted.class, (state, event) -> state.preparingSecurityGroup())
        .onEvent(SecurityGroupPrepared.class, (state, event) -> state.securityGroupPrepared(event.osSecurityGroupId()))
        .onEvent(NetworkPreparationStarted.class, (state, event) -> state.preparingNetwork())
        .onEvent(NetworkPrepared.class, (state, event) -> state.networkPrepared(event.resources()))
        .onEvent(ForwardingPreparationStarted.class, (state, event) -> state.preparingForwarding())
        .onEvent(SchedulingStarted.class, (state, event) -> state.scheduling(event.reservationId()))
        .onEvent(HypervisorReservationStarted.class, (state, event) -> state.reservingResources(
            event.hypervisorId(), event.remainingHypervisorIds()))
        .onEvent(HypervisorSelected.class, (state, event) -> state.hypervisorSelected(event.hypervisorId()))
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
        .onEvent(InstanceDeletionFailed.class, (state, event) -> state.failed(event.failureCode(), event.failureMessage()))
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

  private void recoverState(InstanceWorkflowState state) {
    timers.cancelAll();
    forwardings.reset();
    serverOperations.reset();
    qosUpdateRequestToken = null;
    schedulingRequestToken = null;
    dependencyRequestToken = null;
    resourceReservationRequestToken = null;
    securityGroupRequestToken = null;
    networkPreparationRequestToken = null;
    novaCreationRequestToken = null;
    provisioningCheckRequestToken = null;
    confirmationRequestToken = null;
    deletionRequestToken = null;
    deletionPollAttempt = 0;
    context.getLog().info(
        "workflow recovery completed instanceId={} requestId={} phase={} "
            + "workflowVersion={} hypervisorId={} reservationId={} novaOperationId={} osNovaServerId={}",
        instanceId, state.requestId(), state.phase(), state.version(), state.hypervisorId(),
        state.reservationId(), state.novaOperationId(), state.osNovaServerId());
    if (state.phase().isDeleting()) {
      if (state.phase() == InstancePhase.DELETING_FORWARDINGS) {
        forwardings.recover(state);
        startDeletionStage(state);
      } else {
        // CheckDeletion resumes Nova with a lookup and other stages with their normal requests.
        scheduleDeletionCheck(state);
      }
      return;
    }
    serverOperations.recover(state);
    switch (state.phase()) {
      case SCHEDULING -> startScheduling(state);
      case RESERVING_RESOURCES -> requestResourceReservation(state);
      case PREPARING_DEPENDENCIES -> requestDependencies(state);
      case PREPARING_SECURITY_GROUP -> startSecurityGroupPreparation(state);
      case PREPARING_NETWORK -> requestNetwork(state);
      case PREPARING_FORWARDING -> {
        forwardings.recover(state);
        startForwardingPreparation(state);
      }
      case PROVISIONING -> requestProvisioningCheck(state, 1);
      case CONFIRMING -> scheduleConfirmRetry(state, 0);
      case ACTIVE -> recoverManagement(state);
      default -> { }
    }
  }

  @Override
  public RetentionCriteria retentionCriteria() {
    return RetentionCriteria.snapshotEvery(100, 2);
  }

  private static <T> CompletionStage<T> invoke(Supplier<CompletionStage<T>> operation) {
    try {
      return operation.get();
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(error);
    }
  }

  private static boolean definitiveFailure(Throwable error) {
    if (error instanceof OpenStackOperationException || error instanceof BaseException
        || error instanceof IllegalArgumentException || error instanceof IllegalStateException
        || error instanceof NullPointerException || error instanceof IndexOutOfBoundsException) return true;
    return error instanceof org.openstack4j.api.exceptions.ResponseException response
        && response.getStatus() >= 400 && response.getStatus() < 500
        && response.getStatus() != 408 && response.getStatus() != 429;
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
