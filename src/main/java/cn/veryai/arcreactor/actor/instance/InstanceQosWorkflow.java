package cn.veryai.arcreactor.actor.instance;


import cn.veryai.arcreactor.actor.instance.command.QosUpdateCompleted;
import cn.veryai.arcreactor.actor.instance.command.UpdateInstanceQos;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateFailed;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdateRequested;
import cn.veryai.arcreactor.actor.instance.event.InstanceQosUpdated;
import cn.veryai.arcreactor.actor.instance.event.InstanceWorkflowEvent;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import cn.veryai.arcreactor.openstack.OpenStackClient;
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
  private final EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects;
  private String requestToken;

  InstanceQosWorkflow(String instanceId, ActorContext<WorkflowCommand> context,
      OpenStackClient openStackClient, EffectFactories<InstanceWorkflowEvent, InstanceWorkflowState> effects) {
    this.instanceId = instanceId;
    this.context = context;
    this.openStackClient = openStackClient;
    this.effects = effects;
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onUpdate(
      InstanceWorkflowState state, UpdateInstanceQos command) {
    if (state.phase() != InstancePhase.ACTIVE || state.hasPendingOperation()) {
      command.replyTo().tell(InstanceMutationReply.rejected("Instance must be ACTIVE with no pending management operation"));
      return effects.none();
    }
    InstanceQosUpdate previous = state.qosUpdate();
    if (previous != null && previous.phase() == InstanceQosUpdate.Phase.FAILED
        && (!Objects.equals(previous.parentPort(), command.parentPort())
            || !Objects.equals(previous.subPort(), command.subPort()))) {
      command.replyTo().tell(InstanceMutationReply.rejected("Retry the failed QoS update with the same limits before changing them"));
      return effects.none();
    }
    InstanceQosUpdate update = new InstanceQosUpdate(UUID.randomUUID().toString(), InstanceQosUpdate.Phase.UPDATING,
        command.parentPort(), command.subPort(), null);
    return effects.persist(new InstanceQosUpdateRequested(instanceId, update)).thenRun(next -> {
      command.replyTo().tell(InstanceMutationReply.accepted(update.operationId()));
      request(next);
    });
  }

  private void request(InstanceWorkflowState state) {
    if (requestToken != null) return;
    InstanceQosUpdate update = state.qosUpdate();
    String token = UUID.randomUUID().toString();
    requestToken = token;
    CompletionStage<PreparedNetwork> request;
    try {
      request = openStackClient.updateInstanceQos(target(state), instanceId,
          state.networkPreparation().resources(), update.parentPort(), update.subPort());
    } catch (RuntimeException error) {
      request = CompletableFuture.failedFuture(error);
    }
    context.pipeToSelf(request, (resources, error) -> new QosUpdateCompleted(update.operationId(), token,
        resources, error == null ? null : rootCause(error).toString()));
  }

  Effect<InstanceWorkflowEvent, InstanceWorkflowState> onCompleted(
      InstanceWorkflowState state, QosUpdateCompleted command) {
    InstanceQosUpdate update = state.qosUpdate();
    if (state.phase() != InstancePhase.ACTIVE || update == null || update.phase() != InstanceQosUpdate.Phase.UPDATING
        || !update.operationId().equals(command.operationId()) || requestToken == null
        || !requestToken.equals(command.requestToken())) return effects.none();
    requestToken = null;
    if (command.failure() != null || command.resources() == null) {
      return effects.persist(new InstanceQosUpdateFailed(instanceId,
          update.failed(command.failure() == null ? "QoS update returned no resources" : command.failure())));
    }
    return effects.persist(new InstanceQosUpdated(instanceId, update.completed(), command.resources()));
  }

  void reset() {
    requestToken = null;
  }

  void recover(InstanceWorkflowState state) {
    InstanceQosUpdate update = state.qosUpdate();
    if (state.phase() == InstancePhase.ACTIVE && update != null && update.phase() == InstanceQosUpdate.Phase.UPDATING) {
      // Reapply only the saved intent; the client reads actual rules before repairing a partial update.
      request(state);
    }
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
