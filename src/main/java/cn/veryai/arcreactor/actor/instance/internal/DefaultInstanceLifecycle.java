package cn.veryai.arcreactor.actor.instance.internal;

import cn.veryai.arcreactor.actor.instance.InstanceLifecycle;
import cn.veryai.arcreactor.actor.instance.InstanceWorkflowActor;
import cn.veryai.arcreactor.actor.instance.command.CreateInstance;
import cn.veryai.arcreactor.actor.instance.command.GetInstanceState;
import cn.veryai.arcreactor.actor.instance.command.WorkflowCommand;
import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;

@RequiredArgsConstructor
public final class DefaultInstanceLifecycle implements InstanceLifecycle {
    @NonNull private final ActorSystem<?> system;
    @NonNull private final Duration askTimeout;

    @Override
    public CompletionStage<Submission> create(CreateRequest request) {
        InstanceSpec spec = new InstanceSpec(request.clusterId(), request.projectId(), request.region(),
                request.name(), request.imageId(), request.flavorId(), request.vcpus(), request.memoryGb(),
                request.gpuCount(), request.metadata(), request.share(), request.forwarding(), request.network(),
                request.projectName(), request.cloudInit(), request.hypervisorType());
        return AskPattern.<WorkflowCommand, InstanceReply>ask(
                        entityRef(request.instanceId()),
                        replyTo -> new CreateInstance(request.requestId(), request.instanceId(), spec, replyTo),
                        askTimeout,
                        system.scheduler())
                .thenApply(reply -> new Submission(reply.accepted(), reply.idempotent(), reply.detail()));
    }

    @Override
    public CompletionStage<View> get(String instanceId) {
        return AskPattern.<WorkflowCommand, InstanceWorkflowState>ask(
                        entityRef(instanceId),
                        GetInstanceState::new,
                        askTimeout,
                        system.scheduler())
                .thenApply(this::toView);
    }

    private EntityRef<WorkflowCommand> entityRef(String instanceId) {
        return ClusterSharding.get(system).entityRefFor(InstanceWorkflowActor.TYPE_KEY, instanceId);
    }

    private View toView(InstanceWorkflowState state) {
        return new View(state.instanceId(), state.requestId(), state.phase().name(), state.hypervisorId(),
                state.reservationId(), state.novaOperationId(), state.novaServerId(),
                state.novaStatus() == null ? null : state.novaStatus().name(), state.failureCode(),
                state.failureMessage(), state.version(),
                state.spec() == null || state.spec().share() == null ? null : state.spec().share().userId(),
                state.spec() == null || state.spec().share() == null ? null : state.spec().share().shareId(),
                state.preparedShare() == null ? null : state.preparedShare().osShareId(),
                state.preparedForwarding() == null ? null : state.preparedForwarding().floatingIp(),
                state.preparedForwarding() == null ? 0 : state.preparedForwarding().externalPort(),
                state.networkPreparation() == null ? null : state.networkPreparation().resources(), state.projectId());
    }
}
