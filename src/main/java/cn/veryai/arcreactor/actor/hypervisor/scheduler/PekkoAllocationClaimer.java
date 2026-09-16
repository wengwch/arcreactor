package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorActor;
import cn.veryai.arcreactor.actor.hypervisor.command.HypervisorCommand;
import cn.veryai.arcreactor.actor.hypervisor.command.ReserveResources;
import cn.veryai.arcreactor.actor.hypervisor.reply.ReserveAccepted;
import cn.veryai.arcreactor.actor.hypervisor.reply.ReserveRejected;
import cn.veryai.arcreactor.actor.hypervisor.reply.ReserveReply;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.ClaimResult;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;
import lombok.RequiredArgsConstructor;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AskPattern;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
public final class PekkoAllocationClaimer implements AllocationClaimer {
    private final ActorSystem<?> system;
    private final Duration askTimeout;

    @Override
    public CompletionStage<ClaimResult> tryReserve(HypervisorCandidate candidate, SchedulingRequest request) {
        return AskPattern.<HypervisorCommand, ReserveReply>ask(
                        ClusterSharding.get(system).entityRefFor(
                                HypervisorActor.TYPE_KEY, candidate.hypervisorId()),
                        replyTo -> new ReserveResources(request.requestId(), request.reservationId(),
                                request.instanceId(), request.resources(), request.reservationTtl(), replyTo,
                                request.constraints().availabilityZone(), request.constraints().requiredTraits()),
                        askTimeout,
                        system.scheduler())
                .handle((reply, error) -> {
                    if (error != null) {
                        return isTimeout(error)
                                ? ClaimResult.timeout(request.reservationId(), error.toString())
                                : ClaimResult.failed(request.reservationId(), error.toString());
                    }
                    if (reply instanceof ReserveAccepted accepted) {
                        return ClaimResult.accepted(accepted.reservationId());
                    }
                    ReserveRejected rejected = (ReserveRejected) reply;
                    return ClaimResult.rejected(rejected.reservationId(), rejected.reason(), rejected.detail());
                });
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof java.util.concurrent.TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) return true;
        }
        return false;
    }
}
