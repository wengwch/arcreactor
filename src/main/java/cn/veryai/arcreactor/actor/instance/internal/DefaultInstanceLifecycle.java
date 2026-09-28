package cn.veryai.arcreactor.actor.instance.internal;

import cn.veryai.arcreactor.actor.instance.InstanceLifecycle;
import cn.veryai.arcreactor.actor.instance.InstanceWorkflowEntity;
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
                request.name(), request.imageId(), request.flavorId(), request.vcpus(), request.memoryMb(),
                request.gpuCount(), request.requiredGpuTraits(), request.availabilityZone(),
                request.requiredTraits(), request.metadata());
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
        return ClusterSharding.get(system).entityRefFor(InstanceWorkflowEntity.TYPE_KEY, instanceId);
    }

    private View toView(InstanceWorkflowState state) {
        return new View(state.instanceId(), state.requestId(), state.phase().name(), state.hypervisorId(),
                state.reservationId(), state.novaOperationId(), state.novaServerId(),
                state.novaStatus() == null ? null : state.novaStatus().name(), state.failureCode(),
                state.failureMessage(), state.version());
    }
}
