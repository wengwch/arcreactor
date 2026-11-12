package cn.veryai.arcreactor.actor.hypervisor.internal;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.command.CancelReservation;
import cn.veryai.arcreactor.actor.hypervisor.command.ConfirmReservation;
import cn.veryai.arcreactor.actor.hypervisor.command.HypervisorCommand;
import cn.veryai.arcreactor.actor.hypervisor.command.ReleaseResources;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceRequest;
import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import cn.veryai.arcreactor.actor.hypervisor.reply.ConfirmReply;
import cn.veryai.arcreactor.actor.hypervisor.reply.ReleaseReply;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.SchedulerService;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.ClaimResult;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.PlacementConstraints;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;
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
        return schedulerService.schedule(toSchedulingRequest(request))
                .thenApply(result -> new ScheduleResult(result.hypervisorIds(), result.detail()));
    }

    @Override
    public CompletionStage<ReserveResult> reserve(String hypervisorId, ScheduleRequest request) {
        return schedulerService.reserve(hypervisorId, toSchedulingRequest(request)).thenApply(result -> {
            ReserveResult.Status status = switch (result.status()) {
                case ACCEPTED -> ReserveResult.Status.ACCEPTED;
                case UNKNOWN -> ReserveResult.Status.UNKNOWN;
                case REJECTED -> isUnavailable(result)
                        ? ReserveResult.Status.UNAVAILABLE : ReserveResult.Status.REJECTED;
            };
            return new ReserveResult(status, result.detail());
        });
    }

    private static SchedulingRequest toSchedulingRequest(ScheduleRequest request) {
        Resources resources = request.resources();
        return new SchedulingRequest(
                request.requestId(), request.reservationId(), request.instanceId(),
                new ResourceRequest(resources.vcpus(), resources.memoryGb(), resources.diskGb(),
                        resources.gpuCount()),
                new PlacementConstraints(request.availabilityZone(), request.regionId(),
                        request.hypervisorType(), request.hostname()));
    }

    private static boolean isUnavailable(ClaimResult result) {
        if (result.rejectionReason() == null) return false;
        return switch (result.rejectionReason()) {
            case NOT_ACTIVE, INSUFFICIENT_RESOURCE, INVALID_REQUEST, RESERVATION_UNAVAILABLE -> true;
            case INSTANCE_ALREADY_ALLOCATED, RESERVATION_ID_CONFLICT, RESERVATION_FINALIZED -> false;
        };
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

    @Override
    public CompletionStage<ReservationResult> release(String hypervisorId, String instanceId) {
        return AskPattern.<HypervisorCommand, ReleaseReply>ask(
                        entityRef(hypervisorId),
                        replyTo -> new ReleaseResources(instanceId, replyTo),
                        askTimeout,
                        system.scheduler())
                .handle((reply, error) -> {
                    if (error != null) return ReservationResult.unknown(error.toString());
                    return reply.success()
                            ? ReservationResult.succeeded(reply.idempotent(), "released")
                            : ReservationResult.rejected("resource release rejected");
                });
    }

    private org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef<HypervisorCommand> entityRef(
            String hypervisorId) {
        return ClusterSharding.get(system).entityRefFor(HypervisorActor.TYPE_KEY, hypervisorId);
    }

}
