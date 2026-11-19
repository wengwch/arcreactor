package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.instance.command.CheckForwardingStatus;
import cn.veryai.arcreactor.actor.instance.command.CreateInstanceForwarding;
import cn.veryai.arcreactor.actor.instance.command.DeleteInstanceForwarding;
import cn.veryai.arcreactor.actor.instance.command.ForwardingStatusCompleted;
import cn.veryai.arcreactor.actor.instance.command.InstanceForwardingsChanged;
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

/** Handles forwarding operations in the instance mailbox; lifecycle transitions stay in the instance actor. */
final class InstanceForwardingWorkflow {
  private final String instanceId;
  private final ActorContext<WorkflowCommand> context;
  private final TimerScheduler<WorkflowCommand> timers;
  private final InstanceForwardingOperations operations;
  private final InstanceWorkflowSettings settings;
  private final EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects;
  private final Map<String, String> requests = new HashMap<>();

  InstanceForwardingWorkflow(String instanceId, ActorContext<WorkflowCommand> context,
      TimerScheduler<WorkflowCommand> timers, InstanceForwardingOperations operations,
      InstanceWorkflowSettings settings, EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects) {
    this.instanceId = instanceId;
    this.context = context;
    this.timers = timers;
    this.operations = operations;
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
    return delete(List.of(forwarding))
        .thenReply(command.replyTo(), next -> InstanceMutationReply.accepted(command.claimId()));
  }

  EffectBuilder<InstanceWorkflowEvent, InstanceWorkflowState> create(InstanceForwardingState forwarding) {
    return effects.persist(new ForwardingCreationRequested(instanceId, forwarding))
        .thenRun(next -> request(next, forwarding, 1, true));
  }

  EffectBuilder<InstanceWorkflowEvent, InstanceWorkflowState> delete(List<InstanceForwardingState> forwardings) {
    List<InstanceForwardingState> deletions = forwardings.stream()
        .filter(forwarding -> forwarding.phase() != Phase.DELETING)
        .map(forwarding -> forwarding.transition(Phase.DELETING, null)).toList();
    if (deletions.isEmpty()) return effects.none();
    List<InstanceWorkflowEvent> events = deletions.stream()
        .<InstanceWorkflowEvent>map(forwarding -> new ForwardingDeletionRequested(instanceId, forwarding)).toList();
    return effects.persist(events).thenRun(next -> {
      for (InstanceForwardingState deleting : deletions) request(next, deleting, 1, true);
    });
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
    if (reply == null) return poll(forwarding, command.attempt() + 1);
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
      pipe(forwarding, 1, true, () -> forwarding.phase() == Phase.CREATING
          ? operations.recoverForwardingCreation(state, forwarding)
          : operations.requestDeletion(state, forwarding, false));
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
        ? operations.requestCreation(state, forwarding, submit)
        : operations.requestDeletion(state, forwarding, submit));
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
      if (cause instanceof InstanceForwardingOperations.InvalidForwardingResult) {
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
      context.getSelf().tell(new InstanceForwardingsChanged(next.pollingGeneration()));
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
