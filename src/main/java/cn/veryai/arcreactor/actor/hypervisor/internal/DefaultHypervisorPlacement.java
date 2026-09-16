package cn.veryai.arcreactor.actor.hypervisor.internal;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.command.CancelReservation;
import cn.veryai.arcreactor.actor.hypervisor.command.ConfirmReservation;
import cn.veryai.arcreactor.actor.hypervisor.command.HypervisorCommand;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceRequest;
import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import cn.veryai.arcreactor.actor.hypervisor.reply.ConfirmReply;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.SchedulerService;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.PlacementConstraints;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
public final class DefaultHypervisorPlacement implements HypervisorPlacement {
    @NonNull private final ActorSystem<?> system;
    @NonNull private final SchedulerService schedulerService;
    @NonNull private final Duration askTimeout;

    @Override
    public CompletionStage<ScheduleResult> schedule(ScheduleRequest request) {
        Resources resources = request.resources();
        SchedulingRequest schedulingRequest = new SchedulingRequest(
                request.requestId(), request.reservationId(), request.instanceId(),
                new ResourceRequest(resources.vcpus(), resources.memoryMb(),
                        resources.gpuCount()),
                request.reservationTtl(),
                new PlacementConstraints(request.availabilityZone(), request.requiredTraits()));
        return schedulerService.schedule(schedulingRequest).thenApply(this::toPublicResult);
    }

    @Override
    public CompletionStage<ReservationResult> confirm(
            String hypervisorId, String reservationId, String instanceId) {
        return AskPattern.<HypervisorCommand, ConfirmReply>ask(
                        entityRef(hypervisorId),
                        replyTo -> new ConfirmReservation(reservationId, instanceId, replyTo),
                        askTimeout,
                        system.scheduler())
                .handle((reply, error) -> {
                    if (error != null) return ReservationResult.unknown(error.toString());
                    return reply.success()
                            ? ReservationResult.succeeded(reply.idempotent(), reply.detail())
                            : ReservationResult.rejected(reply.detail());
                });
    }

    @Override
    public CompletionStage<ReservationResult> cancel(String hypervisorId, String reservationId) {
        return AskPattern.<HypervisorCommand, ActionReply>ask(
                        entityRef(hypervisorId),
                        replyTo -> new CancelReservation(reservationId, replyTo),
                        askTimeout,
                        system.scheduler())
                .handle((reply, error) -> {
                    if (error != null) return ReservationResult.unknown(error.toString());
                    return reply.success()
                            ? ReservationResult.succeeded(reply.idempotent(), reply.detail())
                            : ReservationResult.rejected(reply.detail());
                });
    }

    private org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef<HypervisorCommand> entityRef(
            String hypervisorId) {
        return ClusterSharding.get(system).entityRefFor(HypervisorActor.TYPE_KEY, hypervisorId);
    }

    private ScheduleResult toPublicResult(SchedulingResult result) {
        ScheduleResult.Status status = result.status() == SchedulingResult.Status.RESERVED
                ? ScheduleResult.Status.RESERVED
                : ScheduleResult.Status.NO_VALID_HOST;
        return new ScheduleResult(status, result.hypervisorId(), result.claimAttempts(), result.detail());
    }
}
