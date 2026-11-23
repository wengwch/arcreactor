package cn.veryai.arcreactor.actor.instance;

import static org.springframework.util.StringUtils.hasText;

import cn.veryai.arcreactor.actor.fip.FloatingIpForwardings;
import cn.veryai.arcreactor.actor.fip.FloatingIpSettings;
import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;
import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.instance.command.CheckForwardingStatus;
import cn.veryai.arcreactor.actor.instance.command.CreateInstanceForwarding;
import cn.veryai.arcreactor.actor.instance.command.DeleteInstanceForwarding;
import cn.veryai.arcreactor.actor.instance.command.ForwardingStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.ReconcileInstanceForwardings;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCandidateSelected;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCreated;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCreationFailed;
import cn.veryai.arcreactor.actor.instance.event.ForwardingCreationRequested;
import cn.veryai.arcreactor.actor.instance.event.ForwardingDeleted;
import cn.veryai.arcreactor.actor.instance.event.ForwardingDeletionFailed;
import cn.veryai.arcreactor.actor.instance.event.ForwardingDeletionRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceForwardingEvent;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingResources;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState;
import cn.veryai.arcreactor.actor.instance.model.InstanceForwardingState.Phase;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EffectBuilder;
import org.apache.pekko.persistence.typed.javadsl.EffectFactories;

/** Handles forwarding and security group operations in the instance mailbox using the instance's event journal. */
final class InstanceForwardingWorkflow {
  private static final class ForwardingRequestFailed extends RuntimeException {
    ForwardingRequestFailed(Throwable cause) {
      super(cause);
    }
  }

  private static final class InvalidForwardingResult extends IllegalStateException {
    InvalidForwardingResult() {
      super("Incomplete or mismatched port forwarding result");
    }
  }

  private final String instanceId;
  private final ActorContext<WorkflowCommand> context;
  private final TimerScheduler<WorkflowCommand> timers;
  private final OpenStackClient openStackClient;
  private final FloatingIpForwardings floatingIpForwardings;
  private final InstanceWorkflowSettings settings;
  private final EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects;
  private final Map<String, String> requests = new HashMap<>();

  InstanceForwardingWorkflow(String instanceId, ActorContext<WorkflowCommand> context,
      TimerScheduler<WorkflowCommand> timers, OpenStackClient openStackClient, FloatingIpForwardings floatingIpForwardings,
      InstanceWorkflowSettings settings, EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects) {
    this.instanceId = instanceId;
    this.context = context;
    this.timers = timers;
    this.openStackClient = openStackClient;
    this.floatingIpForwardings = floatingIpForwardings;
    this.settings = settings;
    this.effects = effects;
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCreate(
      InstanceWorkflowState state, CreateInstanceForwarding command) {
    if (state.phase() != InstancePhase.ACTIVE || state.hasPendingOperation()) {
      return effects.reply(command.replyTo(), InstanceMutationReply.rejected(
          "Instance must be ACTIVE with no pending management operation"));
    }
    int port = command.internalPort();
    if (state.spec().forwarding().internalPorts().contains(port) || state.forwardingByInternalPort(port) != null) {
      return effects.reply(command.replyTo(), InstanceMutationReply.rejected(
          "The internal port already has a system or user forwarding"));
    }
    List<String> candidates = command.floatingIpIds().isEmpty()
        ? state.spec().forwarding().floatingIpIds() : command.floatingIpIds();
    InstanceForwardingState forwarding = InstanceForwardingState.creating(
        port, ForwardingKind.USER, UUID.randomUUID().toString(), candidates);
    return create(forwarding).thenReply(command.replyTo(), next -> InstanceMutationReply.accepted(forwarding.claimId()));
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onDelete(
      InstanceWorkflowState state, DeleteInstanceForwarding command) {
    if (state.phase() != InstancePhase.ACTIVE) {
      return effects.reply(command.replyTo(), InstanceMutationReply.rejected(
          "Only ACTIVE instances allow user forwarding management"));
    }
    InstanceForwardingState forwarding = state.forwardingByClaimId(command.claimId());
    if (forwarding == null) {
      return effects.reply(command.replyTo(), InstanceMutationReply.accepted(command.claimId()));
    }
    if (forwarding.kind() == ForwardingKind.SYSTEM) {
      return effects.reply(command.replyTo(), InstanceMutationReply.rejected(
          "System forwardings cannot be deleted individually"));
    }
    if (forwarding.phase() == Phase.DELETING) {
      return effects.reply(command.replyTo(), InstanceMutationReply.accepted(command.claimId()));
    }
    if (state.hasPendingOperation()) {
      return effects.reply(command.replyTo(), InstanceMutationReply.rejected(
          "Wait for the pending instance management operation"));
    }
    return delete(forwarding)
        .thenReply(command.replyTo(), next -> InstanceMutationReply.accepted(command.claimId()));
  }

  EffectBuilder<InstanceWorkflowEvent, InstanceWorkflowState> create(InstanceForwardingState forwarding) {
    return effects.persist(new ForwardingCreationRequested(instanceId, forwarding))
        .thenRun(next -> request(next, forwarding, 1, true));
  }

  EffectBuilder<InstanceWorkflowEvent, InstanceWorkflowState> delete(InstanceForwardingState forwarding) {
    if (forwarding.phase() == Phase.DELETING) return effects.none();
    InstanceForwardingState deleting = forwarding.transition(Phase.DELETING, null);
    return effects.persist(new ForwardingDeletionRequested(instanceId, deleting))
        .thenRun(next -> request(next, deleting, 1, true));
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheck(
      InstanceWorkflowState state, CheckForwardingStatus command) {
    InstanceForwardingState forwarding = state.forwardingByClaimId(command.claimId());
    if (matches(state, forwarding, command.generation())) request(state, forwarding, command.attempt(), false);
    return effects.none();
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCompleted(
      InstanceWorkflowState state, ForwardingStatusCompleted command) {
    InstanceForwardingState forwarding = state.forwardingByClaimId(command.claimId());
    if (!matches(state, forwarding, command.generation())
        || !requests.remove(command.claimId(), command.requestToken())) return effects.none();

    if (command.failureCode() != null) {
      return forwarding.phase() == Phase.DELETING
          ? deletionFailed(forwarding, command.failure())
          : creationFailed(forwarding, command.failureCode(), command.failure());
    }
    ForwardingReply reply = command.reply();
    if (reply == null) {
      if (command.failure() != null) {
        context.getLog().warn(
            "Forwarding outcome uncertain: instanceId={} claimId={} floatingIpId={} phase={} attempt={} reason={}",
            instanceId, forwarding.claimId(), forwarding.floatingIpId(), forwarding.phase(), command.attempt(),
            command.failure());
      }
      return poll(forwarding, command.attempt() + 1);
    }
    if (forwarding.phase() == Phase.DELETING) {
      return switch (reply.status()) {
        case DELETED -> completed(new ForwardingDeleted(instanceId, forwarding.transition(Phase.DELETED, null)));
        case DELETE_FAILED, REJECTED -> deletionFailed(forwarding, failure(reply));
        default -> poll(forwarding, command.attempt() + 1);
      };
    }
    return switch (reply.status()) {
      case READY -> completed(new ForwardingCreated(instanceId,
          forwarding.ready(InstanceForwardingResources.from(reply.claim()))));
      case NOT_FOUND -> {
        if (command.submission()) yield poll(forwarding, command.attempt() + 1);
        request(state, forwarding, command.attempt(), true);
        yield effects.none();
      }
      case IN_PROGRESS -> poll(forwarding, command.attempt() + 1);
      case FAILED, DELETE_FAILED, REJECTED, DELETED -> {
        if ((reply.claim() == null || !reply.claim().ownsPort()) && forwarding.hasNextCandidate()) {
          InstanceForwardingState nextCandidate = forwarding.nextCandidate();
          yield effects.persist(new ForwardingCandidateSelected(instanceId, nextCandidate))
              .thenRun(next -> request(next, nextCandidate, 1, true));
        }
        yield creationFailed(forwarding, "FORWARDING_PREPARATION_FAILED", failure(reply));
      }
    };
  }

  void recover(InstanceWorkflowState state) {
    for (InstanceForwardingState forwarding : state.forwardingStates().values()) {
      if (!matches(state, forwarding, forwarding.generation())) continue;
      request(state, forwarding, 1, true);
    }
  }

  void reset() {
    requests.clear();
  }

  boolean inFlight() {
    return !requests.isEmpty();
  }

  private void request(InstanceWorkflowState state, InstanceForwardingState forwarding, int attempt, boolean submit) {
    pipe(forwarding, attempt, submit, () -> forwarding.phase() == Phase.CREATING
        ? requestCreation(state, forwarding, submit)
        : requestDeletion(state, forwarding, submit));
  }

  private CompletionStage<ForwardingReply> requestCreation(InstanceWorkflowState state,
      InstanceForwardingState forwarding, boolean submit) {
    ForwardingSpec spec = targetSpec(state, forwarding);
    return askForwarding(() -> submit ? floatingIpForwardings.create(forwarding.floatingIpId(), forwarding.claimId(), spec)
        : floatingIpForwardings.get(forwarding.floatingIpId(), forwarding.claimId()))
        .thenCompose(reply -> ensureAccessAfterCreation(state, forwarding, spec, reply));
  }

  private CompletionStage<ForwardingReply> requestDeletion(InstanceWorkflowState state,
      InstanceForwardingState forwarding, boolean submit) {
    String floatingIpId = forwarding.floatingIpId();
    String claimId = forwarding.claimId();
    CompletionStage<ForwardingReply> request;
    if (submit) {
      request = floatingIpForwardings.delete(floatingIpId, claimId);
    } else {
      request = floatingIpForwardings.get(floatingIpId, claimId).thenCompose(reply -> {
        if (reply == null) return CompletableFuture.completedFuture(null);
        boolean retryDelete = switch (reply.status()) {
          case READY, FAILED, NOT_FOUND -> true;
          case DELETED, IN_PROGRESS, REJECTED, DELETE_FAILED -> false;
        };
        return retryDelete ? floatingIpForwardings.delete(floatingIpId, claimId) : CompletableFuture.completedFuture(reply);
      });
    }
    return request.thenCompose(reply -> cleanAccessAfterDeletion(state, forwarding.internalPort(), reply));
  }

  private static ForwardingSpec targetSpec(InstanceWorkflowState state, InstanceForwardingState forwarding) {
    var network = state.networkPreparation().resources();
    return new ForwardingSpec(state.instanceId(), state.userId(), state.spec().region(), state.osProjectId(),
        network.osParentPortId(), network.parentFixedIp(), forwarding.internalPort(), forwarding.kind());
  }

  /** Preserve ask failures separately from access errors: the claim outcome is uncertain, so poll the same claim. */
  private static CompletionStage<ForwardingReply> askForwarding(Supplier<CompletionStage<ForwardingReply>> request) {
    try {
      return request.get().exceptionally(error -> { throw new ForwardingRequestFailed(error); });
    } catch (RuntimeException error) {
      return CompletableFuture.failedFuture(new ForwardingRequestFailed(error));
    }
  }

  private static boolean matchesReady(InstanceForwardingState forwarding, ForwardingSpec expected, ForwardingClaim claim) {
    if (claim == null || claim.phase() != ForwardingClaim.Phase.READY || !expected.equals(claim.spec())
        || !forwarding.claimId().equals(claim.claimId()) || !forwarding.floatingIpId().equals(claim.floatingIpId())
        || !hasText(claim.osFloatingIpId())) return false;
    var resources = claim.resources();
    return hasText(resources.floatingIpAddress()) && hasText(resources.osTcpForwardingId())
        && hasText(resources.osUdpForwardingId()) && resources.externalPort() >= FloatingIpSettings.FIRST_PORT
        && resources.externalPort() <= FloatingIpSettings.LAST_PORT;
  }

  private CompletionStage<ForwardingReply> ensureAccessAfterCreation(InstanceWorkflowState state,
      InstanceForwardingState forwarding, ForwardingSpec spec, ForwardingReply reply) {
    if (reply == null || reply.status() != ForwardingReply.Status.READY) {
      return CompletableFuture.completedFuture(reply);
    }
    if (!matchesReady(forwarding, spec, reply.claim())) throw new InvalidForwardingResult();
    return openStackClient.ensureInstanceForwardingAccess(target(state), state.instanceId(), state.osSecurityGroupId(),
        spec.internalPort()).thenApply(ignored -> reply);
  }

  private CompletionStage<ForwardingReply> cleanAccessAfterDeletion(InstanceWorkflowState state,
      int internalPort, ForwardingReply reply) {
    if (reply == null || reply.status() != ForwardingReply.Status.DELETED) {
      return CompletableFuture.completedFuture(reply);
    }
    return openStackClient.deleteInstanceForwardingAccess(target(state), state.instanceId(), state.osSecurityGroupId(),
        internalPort).thenApply(ignored -> reply);
  }

  private static OpenStackClient.ServerTarget target(InstanceWorkflowState state) {
    return new OpenStackClient.ServerTarget(state.spec().clusterId(), state.osProjectId(), state.spec().region());
  }

  private void pipe(InstanceForwardingState forwarding, int attempt, boolean submission,
      Supplier<CompletionStage<ForwardingReply>> operation) {
    String token = UUID.randomUUID().toString();
    if (requests.putIfAbsent(forwarding.claimId(), token) != null) return;
    CompletionStage<ForwardingReply> request;
    try { request = operation.get(); }
    catch (RuntimeException error) { request = CompletableFuture.failedFuture(error); }
    context.pipeToSelf(request, (reply, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      String failureCode = null;
      if (cause instanceof ForwardingRequestFailed requestFailure) {
        cause = rootCause(requestFailure.getCause());
      } else if (cause instanceof InvalidForwardingResult) {
        failureCode = "FORWARDING_RESULT_INVALID";
      } else if (definitiveFailure(cause)) {
        failureCode = forwarding.phase() == Phase.CREATING ? "SECURITY_GROUP_RULE_FAILED" : "FORWARDING_DELETE_FAILED";
      }
      return new ForwardingStatusCompleted(forwarding.claimId(), forwarding.generation(), token, attempt, submission,
          reply, failureCode, cause == null ? null : cause.toString());
    });
  }

  private static boolean matches(InstanceWorkflowState state, InstanceForwardingState forwarding, long generation) {
    if (forwarding == null || !forwarding.inProgress() || forwarding.generation() != generation) return false;
    if (forwarding.phase() == Phase.DELETING) {
      return state.phase() == InstancePhase.ACTIVE || state.phase() == InstancePhase.DELETING_FORWARDINGS;
    }
    return state.phase() == (forwarding.kind() == ForwardingKind.SYSTEM
        ? InstancePhase.PREPARING_FORWARDING : InstancePhase.ACTIVE);
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> creationFailed(
      InstanceForwardingState forwarding, String code, String detail) {
    return completed(new ForwardingCreationFailed(instanceId, forwarding.failed(code, detail)));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> deletionFailed(
      InstanceForwardingState forwarding, String detail) {
    return completed(new ForwardingDeletionFailed(instanceId, forwarding.transition(Phase.DELETE_FAILED, detail)));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> completed(InstanceForwardingEvent event) {
    return effects.persist(event).thenRun(next -> {
      timers.cancel(timerKey(event.forwarding()));
      context.getSelf().tell(new ReconcileInstanceForwardings(next.pollingGeneration()));
    });
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> poll(InstanceForwardingState forwarding, int attempt) {
    Duration delay = forwarding.phase() == Phase.DELETING ? settings.pollInitialDelay() : settings.pollDelay(attempt);
    timers.startSingleTimer(timerKey(forwarding),
        new CheckForwardingStatus(forwarding.claimId(), forwarding.generation(), attempt), delay);
    return effects.none();
  }

  private static String timerKey(InstanceForwardingState forwarding) {
    return "forwarding:" + forwarding.claimId();
  }

  private static String failure(ForwardingReply reply) {
    return reply.detail() == null ? reply.status().name() : reply.detail();
  }

  private static boolean definitiveFailure(Throwable error) {
    if (error instanceof OpenStackOperationException || error instanceof IllegalArgumentException
        || error instanceof IllegalStateException) return true;
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
}
