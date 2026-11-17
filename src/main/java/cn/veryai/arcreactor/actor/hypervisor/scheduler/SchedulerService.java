package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.PekkoRuntime;
import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import cn.veryai.arcreactor.actor.hypervisor.command.CancelReservation;
import cn.veryai.arcreactor.actor.hypervisor.command.ConfirmReservation;
import cn.veryai.arcreactor.actor.hypervisor.command.HypervisorCommand;
import cn.veryai.arcreactor.actor.hypervisor.command.ReleaseResources;
import cn.veryai.arcreactor.actor.hypervisor.command.ReserveResources;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceRequest;
import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import cn.veryai.arcreactor.actor.hypervisor.reply.ConfirmReply;
import cn.veryai.arcreactor.actor.hypervisor.reply.ReleaseReply;
import cn.veryai.arcreactor.actor.hypervisor.reply.ReserveAccepted;
import cn.veryai.arcreactor.actor.hypervisor.reply.ReserveRejected;
import cn.veryai.arcreactor.actor.hypervisor.reply.ReserveReply;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.filter.CapacityFilter;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.filter.GpuFilter;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.filter.HostnameFilter;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.filter.SchedulerFilter;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.ScoredCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer.CandidateScorer;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer.CpuBalanceScorer;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer.MemoryBalanceScorer;
import cn.veryai.arcreactor.entity.HypervisorHostEntity;
import cn.veryai.arcreactor.repo.HypervisorHostRepo;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pekko.actor.typed.javadsl.AskPattern;

/** Ranks projected hosts and sends resource commands directly to their actors. */
@Slf4j
public final class SchedulerService implements HypervisorPlacement {
    private final PekkoRuntime runtime;
    private final HypervisorHostRepo hostRepo;
    private final Executor blockingExecutor;
    private final int queryLimit;
    private final int maxClaimAttempts;
    private final List<SchedulerFilter> filters = List.of(
            new HostnameFilter(), new CapacityFilter(), new GpuFilter());
    private final List<WeightedScorer> scorers = List.of(
            new WeightedScorer(new CpuBalanceScorer(), 0.5),
            new WeightedScorer(new MemoryBalanceScorer(), 0.5));

    public SchedulerService(PekkoRuntime runtime, HypervisorHostRepo hostRepo, Executor blockingExecutor,
            int queryLimit, int maxClaimAttempts) {
        this.runtime = Objects.requireNonNull(runtime);
        this.hostRepo = Objects.requireNonNull(hostRepo);
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor);
        if (queryLimit <= 0) throw new IllegalArgumentException("queryLimit must be positive");
        if (maxClaimAttempts <= 0) throw new IllegalArgumentException("maxClaimAttempts must be positive");
        this.queryLimit = queryLimit;
        this.maxClaimAttempts = maxClaimAttempts;
    }

    @Override
    public CompletionStage<ScheduleResult> schedule(ScheduleRequest request) {
        Objects.requireNonNull(request, "request");
        return CompletableFuture.supplyAsync(() -> findCandidates(request), blockingExecutor)
                .thenApply(candidates -> {
                    List<ScoredCandidate> ranked = rank(candidates, request);
                    log.info("scheduler ranked requestId={} instanceId={} candidates={} eligible={}",
                            request.requestId(), request.instanceId(), candidates.size(), ranked.size());
                    List<String> hypervisorIds = ranked.stream()
                            .limit(maxClaimAttempts)
                            .map(candidate -> candidate.candidate().hypervisorId())
                            .toList();
                    return new ScheduleResult(hypervisorIds,
                            hypervisorIds.isEmpty() ? "no eligible hosts" : "candidates ranked");
                });
    }

    private List<HypervisorCandidate> findCandidates(ScheduleRequest request) {
        Resources resources = request.resources();
        return hostRepo.findCandidates(resources.vcpus(), resources.memoryGb(), resources.diskGb(),
                        resources.gpuCount(), request.regionId(),
                        request.hypervisorType(), request.hostname(), queryLimit).stream()
                .map(SchedulerService::mapCandidate)
                .toList();
    }

    private static HypervisorCandidate mapCandidate(HypervisorHostEntity host) {
        return new HypervisorCandidate(host.getId(), host.getHypervisorStatus(), host.getHostname(),
                host.getVcpus(), host.getReservedVcpu(), host.getUsedVcpus(),
                host.getRam(), host.getReservedMemoryGb(), host.getUsedRam(),
                host.getDisk(), host.getReservedDiskGb(), host.getUsedDisk(),
                host.getGpus(), host.getReservedGpu(), host.getUsedGpus(),
                host.getResourceVersion());
    }

    private List<ScoredCandidate> rank(List<HypervisorCandidate> candidates, ScheduleRequest request) {
        Comparator<ScoredCandidate> order = Comparator.comparingDouble(ScoredCandidate::score).reversed();
        if (request.resources().gpuCount() > 0) {
            // CPU/memory scores break ties only after packing GPU reservations into the fullest eligible hosts.
            order = Comparator.comparingInt((ScoredCandidate scored) -> scored.candidate().availableGpuCount())
                    .thenComparing(order);
        }
        return candidates.stream()
                .filter(candidate -> filters.stream().allMatch(filter -> filter.matches(request, candidate)))
                .map(candidate -> new ScoredCandidate(candidate, scorers.stream()
                        .mapToDouble(weighted -> weighted.weight() * weighted.scorer().score(request, candidate))
                        .sum()))
                .sorted(order.thenComparing(scored -> scored.candidate().hypervisorId()))
                .toList();
    }

    /** Reserves only the host already persisted by the instance workflow. */
    @Override
    public CompletionStage<ReserveResult> reserve(String hypervisorId, ScheduleRequest request) {
        Objects.requireNonNull(hypervisorId, "hypervisorId");
        Objects.requireNonNull(request, "request");
        Resources resources = request.resources();
        CompletionStage<ReserveReply> reservation;
        try {
            reservation = AskPattern.<HypervisorCommand, ReserveReply>ask(
                    runtime.hypervisorActor(hypervisorId),
                    replyTo -> new ReserveResources(request.requestId(), request.reservationId(),
                            request.instanceId(), new ResourceRequest(resources.vcpus(), resources.memoryGb(),
                                    resources.diskGb(), resources.gpuCount()),
                            replyTo),
                    runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler());
        } catch (RuntimeException error) {
            reservation = CompletableFuture.failedFuture(error);
        }
        return reservation.handle((reply, error) -> {
            ReserveResult result;
            if (error != null || reply == null) {
                result = new ReserveResult(ReserveResult.Status.UNKNOWN,
                        error == null ? "reservation returned no result" : error.toString());
            } else if (reply instanceof ReserveAccepted) {
                result = new ReserveResult(ReserveResult.Status.ACCEPTED, "accepted");
            } else if (reply instanceof ReserveRejected rejected) {
                result = new ReserveResult(isUnavailable(rejected.reason())
                        ? ReserveResult.Status.UNAVAILABLE : ReserveResult.Status.REJECTED, rejected.detail());
            } else {
                result = new ReserveResult(ReserveResult.Status.UNKNOWN, "unexpected reservation reply");
            }
            log.info("scheduler claim completed hypervisorId={} requestId={} reservationId={} "
                            + "instanceId={} outcome={} detail={}", hypervisorId, request.requestId(),
                    request.reservationId(), request.instanceId(), result.status(), result.detail());
            return result;
        });
    }

    private static boolean isUnavailable(ReserveRejected.Reason reason) {
        if (reason == null) return false;
        return switch (reason) {
            case NOT_ACTIVE, INSUFFICIENT_RESOURCE, RESERVATION_UNAVAILABLE -> true;
            case INSTANCE_ALREADY_ALLOCATED, RESERVATION_ID_CONFLICT, RESERVATION_FINALIZED -> false;
        };
    }

    @Override
    public CompletionStage<ReservationResult> confirm(
            String hypervisorId, String reservationId, String instanceId) {
        return AskPattern.<HypervisorCommand, ConfirmReply>ask(
                        runtime.hypervisorActor(hypervisorId),
                        replyTo -> new ConfirmReservation(reservationId, instanceId, replyTo),
                        runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler())
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
                        runtime.hypervisorActor(hypervisorId),
                        replyTo -> new CancelReservation(reservationId, replyTo),
                        runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler())
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
                        runtime.hypervisorActor(hypervisorId),
                        replyTo -> new ReleaseResources(instanceId, replyTo),
                        runtime.getConfig().getAskTimeout(), runtime.getActorSystem().scheduler())
                .handle((reply, error) -> {
                    if (error != null) return ReservationResult.unknown(error.toString());
                    return reply.success()
                            ? ReservationResult.succeeded(reply.idempotent(), "released")
                            : ReservationResult.rejected("resource release rejected");
                });
    }

    private record WeightedScorer(CandidateScorer scorer, double weight) {}
}
