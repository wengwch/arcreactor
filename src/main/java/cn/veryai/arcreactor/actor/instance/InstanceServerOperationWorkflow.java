package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.instance.command.CheckServerOperation;
import cn.veryai.arcreactor.actor.instance.command.RebootInstance;
import cn.veryai.arcreactor.actor.instance.command.RebuildInstance;
import cn.veryai.arcreactor.actor.instance.command.ServerOperationChecked;
import cn.veryai.arcreactor.actor.instance.command.ServerOperationSubmitted;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationCompleted;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceServerOperationSubmitted;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceServerOperation;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import cn.veryai.arcreactor.openstack.OpenStackOperationException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EffectFactories;

/** Handles reboot and rebuild operations in the instance mailbox using the instance's event journal. */
final class InstanceServerOperationWorkflow {
  private static final String POLL_TIMER = "instance-server-operation";

  private final String instanceId;
  private final ActorContext<WorkflowCommand> context;
  private final TimerScheduler<WorkflowCommand> timers;
  private final OpenStackClient openStackClient;
  private final InstanceSshChecker sshChecker;
  private final InstanceWorkflowSettings settings;
  private final Clock clock;
  private final EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects;
  private String requestToken;

  InstanceServerOperationWorkflow(String instanceId, ActorContext<WorkflowCommand> context,
      TimerScheduler<WorkflowCommand> timers, OpenStackClient openStackClient, InstanceSshChecker sshChecker,
      InstanceWorkflowSettings settings, Clock clock,
      EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects) {
    this.instanceId = instanceId;
    this.context = context;
    this.timers = timers;
    this.openStackClient = openStackClient;
    this.sshChecker = sshChecker;
    this.settings = settings;
    this.clock = clock;
    this.effects = effects;
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onReboot(
      InstanceWorkflowState state, RebootInstance command) {
    return onOperation(state, InstanceServerOperation.Type.REBOOT, null, command.replyTo());
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onRebuild(
      InstanceWorkflowState state, RebuildInstance command) {
    return onOperation(state, InstanceServerOperation.Type.REBUILD, command.osImageId(), command.replyTo());
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> onOperation(
      InstanceWorkflowState state, InstanceServerOperation.Type type, String osImageId,
      ActorRef<InstanceMutationReply> replyTo) {
    InstanceServerOperation previous = state.serverOperation();
    if (previous != null && matches(state, previous.operationId())
        && previous.type() == type && Objects.equals(previous.osImageId(), osImageId)) {
      replyTo.tell(InstanceMutationReply.accepted(previous.operationId()));
      return effects.none();
    }
    boolean retrying = state.phase() == InstancePhase.FAILED && previous != null
        && previous.phase() == InstanceServerOperation.Phase.FAILED;
    if ((state.phase() != InstancePhase.ACTIVE && !retrying) || state.hasPendingOperation()) {
      replyTo.tell(InstanceMutationReply.rejected(
          "Instance must be ACTIVE or have a failed server operation, with no pending management operation"));
      return effects.none();
    }
    if (state.osNovaServerId() == null || state.osNovaServerId().isBlank()) {
      replyTo.tell(InstanceMutationReply.rejected("Instance has no recorded Nova server"));
      return effects.none();
    }
    InstanceServerOperation operation = new InstanceServerOperation(UUID.randomUUID().toString(), type,
        InstanceServerOperation.Phase.SUBMITTING, osImageId, clock.instant(), null);
    return effects.persist(new InstanceServerOperationRequested(instanceId, operation)).thenRun(next -> {
      replyTo.tell(InstanceMutationReply.accepted(operation.operationId()));
      submit(next);
    });
  }

  private void submit(InstanceWorkflowState state) {
    InstanceServerOperation operation = state.serverOperation();
    String token = UUID.randomUUID().toString();
    requestToken = token;
    CompletionStage<Void> request;
    try {
      request = operation.type() == InstanceServerOperation.Type.REBOOT
          ? openStackClient.rebootServer(target(state), state.osNovaServerId())
          : openStackClient.rebuildServer(target(state), state.osNovaServerId(), operation.osImageId());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (ignored, error) -> {
      Throwable cause = error == null ? null : rootCause(error);
      return new ServerOperationSubmitted(operation.operationId(), token,
          definitiveFailure(cause), cause == null ? null : cause.toString());
    });
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onSubmitted(
      InstanceWorkflowState state, ServerOperationSubmitted command) {
    if (!matches(state, command.operationId()) || requestToken == null
        || !requestToken.equals(command.requestToken())) return effects.none();
    requestToken = null;
    InstanceServerOperation operation = state.serverOperation();
    if (operation.phase() != InstanceServerOperation.Phase.SUBMITTING) return effects.none();
    if (command.rejected()) return fail(state, "REJECTED", command.failure(), null);
    if (command.failure() != null) {
      // A timeout is not permission to repeat a destructive action; reconcile using read-only checks.
      context.getLog().warn("Nova server operation acceptance uncertain: instanceId={} operationId={} reason={}",
          instanceId, operation.operationId(), command.failure());
      scheduleCheck(state, 1);
      return effects.none();
    }
    return effects.persist(new InstanceServerOperationSubmitted(instanceId, operation.running()))
        .thenRun(next -> scheduleCheck(next, 1));
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCheck(
      InstanceWorkflowState state, CheckServerOperation command) {
    if (matches(state, command.operationId()) && requestToken == null) {
      requestCheck(state, command.attempt());
    }
    return effects.none();
  }

  private void requestCheck(InstanceWorkflowState state, int attempt) {
    InstanceServerOperation operation = state.serverOperation();
    String token = UUID.randomUUID().toString();
    requestToken = token;
    CompletionStage<OpenStackClient.NovaServer> request;
    try {
      request = openStackClient.getServer(target(state), state.osNovaServerId());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    var check = request.thenCompose(server -> {
      if (operation.phase() != InstanceServerOperation.Phase.RUNNING || !ready(operation, server)
          || !state.osNovaServerId().equals(server.osServerId())) {
        return CompletableFuture.completedFuture(new ServerOperationChecked(operation.operationId(), token,
            attempt, server, false, false, null));
      }
      return sshChecker.check(state).thenApply(connected -> new ServerOperationChecked(operation.operationId(),
          token, attempt, server, Boolean.TRUE.equals(connected), false, null));
    });
    context.pipeToSelf(check, (result, error) -> {
      if (error == null) return result;
      Throwable cause = rootCause(error);
      return new ServerOperationChecked(operation.operationId(), token, attempt, null, false,
          definitiveFailure(cause), cause.toString());
    });
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onChecked(
      InstanceWorkflowState state, ServerOperationChecked command) {
    if (!matches(state, command.operationId()) || requestToken == null
        || !requestToken.equals(command.requestToken())) return effects.none();
    requestToken = null;
    if (command.failure() != null) {
      if (command.rejected()) return fail(state, "CHECK_FAILED", command.failure(), null);
      scheduleCheck(state, command.attempt() + 1);
      return effects.none();
    }
    OpenStackClient.NovaServer server = command.server();
    if (server == null) {
      return fail(state, "SERVER_MISSING", "The recorded Nova server no longer exists", null);
    }
    if (!state.osNovaServerId().equals(server.osServerId())) {
      return fail(state, "RESULT_INVALID", "Nova returned a different server", null);
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ERROR && serverIdle(server)) {
      return fail(state, "NOVA_ERROR",
          server.fault() == null ? "Nova server entered ERROR" : server.fault(), server.status());
    }
    InstanceServerOperation operation = state.serverOperation();
    if (operation.phase() == InstanceServerOperation.Phase.SUBMITTING) {
      // Recovery may observe an accepted action whose response was never persisted.
      boolean imageChanged = operation.type() == InstanceServerOperation.Type.REBUILD
          && operation.osImageId().equals(server.osImageId())
          && !operation.osImageId().equals(state.osImageId());
      if (observed(operation, server) || imageChanged) {
        return effects.persist(new InstanceServerOperationSubmitted(instanceId, operation.running()))
            .thenRun(next -> scheduleCheck(next, 1));
      }
      return fail(state, "UNCONFIRMED",
          "Nova acceptance could not be confirmed; the server action was not resubmitted", server.status());
    }
    if (server.status() == OpenStackClient.NovaServer.Status.ACTIVE && serverIdle(server)
        && operation.type() == InstanceServerOperation.Type.REBUILD
        && !operation.osImageId().equals(server.osImageId())) {
      return fail(state, "IMAGE_MISMATCH", "Nova server is not using the requested image", server.status());
    }
    if (ready(operation, server) && command.sshConnected()) {
      return effects.persist(new InstanceServerOperationCompleted(instanceId, operation.succeeded(), server.osImageId()))
          .thenRun(next -> timers.cancel(POLL_TIMER));
    }
    scheduleCheck(state, command.attempt() + 1);
    return effects.none();
  }

  private boolean matches(InstanceWorkflowState state, String operationId) {
    InstanceServerOperation operation = state.serverOperation();
    return operation != null && operation.inProgress() && operation.operationId().equals(operationId)
        && state.phase() == operation.type().instancePhase();
  }

  private static boolean serverIdle(OpenStackClient.NovaServer server) {
    return server.taskState() == null || server.taskState().isBlank();
  }

  private static boolean ready(InstanceServerOperation operation, OpenStackClient.NovaServer server) {
    return server != null && server.status() == OpenStackClient.NovaServer.Status.ACTIVE && serverIdle(server)
        && (operation.type() == InstanceServerOperation.Type.REBOOT || operation.osImageId().equals(server.osImageId()));
  }

  private static boolean observed(InstanceServerOperation operation, OpenStackClient.NovaServer server) {
    String task = server.taskState();
    return operation.type() == InstanceServerOperation.Type.REBOOT
        ? server.status() == OpenStackClient.NovaServer.Status.REBOOT
            || server.status() == OpenStackClient.NovaServer.Status.HARD_REBOOT
            || (task != null && task.startsWith("reboot"))
        : server.status() == OpenStackClient.NovaServer.Status.REBUILD
            || (task != null && task.startsWith("rebuild"));
  }

  private Effect<InstanceWorkflowEvent, InstanceWorkflowState> fail(InstanceWorkflowState state,
      String code, String detail, OpenStackClient.NovaServer.Status status) {
    return effects.persist(new InstanceServerOperationFailed(instanceId, state.serverOperation().failed(detail),
        status, "NOVA_" + state.serverOperation().type().name() + "_" + code))
        .thenRun(next -> timers.cancel(POLL_TIMER));
  }

  private void scheduleCheck(InstanceWorkflowState state, int attempt) {
    timers.startSingleTimer(POLL_TIMER,
        new CheckServerOperation(state.serverOperation().operationId(), attempt), settings.pollDelay(attempt));
  }

  void reset() {
    timers.cancel(POLL_TIMER);
    requestToken = null;
  }

  void recover(InstanceWorkflowState state) {
    InstanceServerOperation operation = state.serverOperation();
    if (operation != null && matches(state, operation.operationId())) {
      // Reboot/rebuild are not idempotent. Recovery only observes the saved operation.
      scheduleCheck(state, 1);
    }
  }

  private static OpenStackClient.ServerTarget target(InstanceWorkflowState state) {
    return new OpenStackClient.ServerTarget(
        state.spec().clusterId(), state.osProjectId(), state.spec().region());
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
