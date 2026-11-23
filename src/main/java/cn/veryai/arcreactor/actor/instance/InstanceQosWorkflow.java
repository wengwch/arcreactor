package cn.veryai.arcreactor.actor.instance;


import cn.veryai.arcreactor.actor.instance.command.ExpireInstanceQos;
import cn.veryai.arcreactor.actor.instance.command.QosUpdateCompleted;
import cn.veryai.arcreactor.actor.instance.command.UpdateInstanceQos;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdated;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosExpired;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosExpiryFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosExpiryRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EffectFactories;

/** Handles QoS updates in the instance mailbox using the instance's event journal. */
final class InstanceQosWorkflow {
  private final String instanceId;
  private final ActorContext<WorkflowCommand> context;
  private final OpenStackClient openStackClient;
  private final Clock clock;
  private final EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects;
  private String requestToken;

  InstanceQosWorkflow(String instanceId, ActorContext<WorkflowCommand> context,
      OpenStackClient openStackClient, Clock clock,
      EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects) {
    this.instanceId = instanceId;
    this.context = context;
    this.openStackClient = openStackClient;
    this.clock = clock;
    this.effects = effects;
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onUpdate(
      InstanceWorkflowState state, UpdateInstanceQos command) {
    if (state.phase() != InstancePhase.ACTIVE || state.hasPendingOperation()) {
      command.replyTo().tell(InstanceMutationReply.rejected("Instance must be ACTIVE with no pending management operation"));
      return effects.none();
    }
    InstanceQosUpdate update = new InstanceQosUpdate(UUID.randomUUID().toString(), InstanceQosUpdate.Phase.UPDATING,
        command.parentPort(), command.subPort(), null, command.expiresAt(), null, null);
    return effects.persist(new InstanceQosUpdateRequested(instanceId, update)).thenRun(next -> {
      command.replyTo().tell(InstanceMutationReply.accepted(update.operationId()));
      request(next);
    });
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onExpire(
      InstanceWorkflowState state, ExpireInstanceQos command) {
    InstanceQosUpdate current = state.activeQos();
    if (current == null || !current.operationId().equals(command.operationId())
        || state.phase() == InstancePhase.DELETED) {
      command.replyTo().tell(new InstanceMutationReply(true, command.operationId(), "QoS record is no longer current"));
      return effects.none();
    }
    if (current.phase() == InstanceQosUpdate.Phase.EXPIRING) {
      if (!Objects.equals(current.expiryParentPort(), command.parentPort())
          || !Objects.equals(current.expirySubPort(), command.subPort())) {
        command.replyTo().tell(InstanceMutationReply.rejected("QoS expiry is in progress with different reset limits"));
        return effects.none();
      }
      command.replyTo().tell(InstanceMutationReply.accepted(current.operationId()));
      return effects.none();
    }
    if (state.phase() != InstancePhase.ACTIVE || state.hasPendingOperation()) {
      command.replyTo().tell(InstanceMutationReply.rejected("Instance must be ACTIVE with no pending management operation"));
      return effects.none();
    }
    if (current.expiresAt() == null || current.expiresAt().isAfter(clock.instant())) {
      command.replyTo().tell(InstanceMutationReply.rejected("QoS record has not reached its expiry time"));
      return effects.none();
    }
    InstanceQosUpdate update = current.expiring(command.parentPort(), command.subPort());
    return effects.persist(new InstanceQosExpiryRequested(instanceId, update)).thenRun(next -> {
      command.replyTo().tell(InstanceMutationReply.accepted(update.operationId()));
      request(next);
    });
  }

  private void request(InstanceWorkflowState state) {
    InstanceQosUpdate update = state.inProgressQos();
    if (state.phase() != InstancePhase.ACTIVE || update == null || requestToken != null) return;
    String token = UUID.randomUUID().toString();
    requestToken = token;
    CompletionStage<PreparedNetwork> request;
    try {
      boolean expiring = update.phase() == InstanceQosUpdate.Phase.EXPIRING;
      request = openStackClient.updateInstanceQos(target(state), instanceId,
          state.networkPreparation().resources(),
          expiring ? update.expiryParentPort() : update.parentPort(),
          expiring ? update.expirySubPort() : update.subPort());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (resources, error) -> new QosUpdateCompleted(update.operationId(), token,
        resources, error == null ? null : rootCause(error).toString()));
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCompleted(
      InstanceWorkflowState state, QosUpdateCompleted command) {
    InstanceQosUpdate update = state.inProgressQos();
    if (state.phase() != InstancePhase.ACTIVE || update == null
        || !update.operationId().equals(command.operationId()) || requestToken == null
        || !requestToken.equals(command.requestToken())) return effects.none();
    requestToken = null;
    boolean expiring = update.phase() == InstanceQosUpdate.Phase.EXPIRING;
    if (command.failure() != null || command.resources() == null) {
      InstanceQosUpdate failed = update.failed(command.failure() == null ? "QoS update returned no resources" : command.failure());
      return effects.persist(expiring ? new InstanceQosExpiryFailed(instanceId, failed)
          : new InstanceQosUpdateFailed(instanceId, failed));
    }
    if (expiring) {
      return effects.persist(new InstanceQosExpired(instanceId, update.completed(), command.resources()));
    }
    String replacedQosId = state.activeQos() == null ? null : state.activeQos().operationId();
    return effects.persist(new InstanceQosUpdated(instanceId, update.completed(), command.resources(),
        clock.instant(), replacedQosId));
  }

  void reset() {
    requestToken = null;
  }

  void recover(InstanceWorkflowState state) {
    // Reapply only the saved in-progress intent; failed records wait for an explicit retry.
    request(state);
  }

  private static OpenStackClient.ServerTarget target(InstanceWorkflowState state) {
    return new OpenStackClient.ServerTarget(state.spec().clusterId(), state.osProjectId(), state.spec().region());
  }

  private static Throwable rootCause(Throwable error) {
    Throwable current = error;
    while ((current instanceof CompletionException || current instanceof ExecutionException)
        && current.getCause() != null) current = current.getCause();
    return current;
  }
}
