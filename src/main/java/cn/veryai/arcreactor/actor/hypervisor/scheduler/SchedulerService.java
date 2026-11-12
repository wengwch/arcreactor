package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.filter.SchedulerFilter;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.*;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer.CandidateScorer;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Slf4j
public final class SchedulerService {
    private final CandidateFinder candidateFinder;
    private final List<SchedulerFilter> filters;
    private final List<WeightedScorer> scorers;
    private final AllocationClaimer allocationClaimer;
    private final int maxClaimAttempts;
    private final SchedulerMetrics metrics;

    public SchedulerService(CandidateFinder candidateFinder, List<SchedulerFilter> filters,
            List<WeightedScorer> scorers, AllocationClaimer allocationClaimer, int maxClaimAttempts,
            SchedulerMetrics metrics) {
        this.candidateFinder = Objects.requireNonNull(candidateFinder);
        this.filters = List.copyOf(filters);
        this.scorers = List.copyOf(scorers);
        this.allocationClaimer = Objects.requireNonNull(allocationClaimer);
        if (maxClaimAttempts <= 0) throw new IllegalArgumentException("maxClaimAttempts must be positive");
        this.maxClaimAttempts = maxClaimAttempts;
        this.metrics = Objects.requireNonNullElse(metrics, SchedulerMetrics.NOOP);
    }

    public CompletionStage<SchedulingResult> schedule(SchedulingRequest request) {
        metrics.request();
        return candidateFinder.find(request).thenApply(candidates -> {
            List<ScoredCandidate> ranked = rank(candidates, request);
            log.info("scheduler ranked requestId={} instanceId={} candidates={} eligible={}",
                    request.requestId(), request.instanceId(), candidates.size(), ranked.size());
            List<String> hypervisorIds = ranked.stream()
                    .limit(maxClaimAttempts)
                    .map(candidate -> candidate.candidate().hypervisorId())
                    .toList();
            if (hypervisorIds.isEmpty()) metrics.noValidHost();
            return new SchedulingResult(hypervisorIds,
                    hypervisorIds.isEmpty() ? "no eligible hosts" : "candidates ranked");
        });
    }

    /** Attempts one reservation on the already selected host, without querying or changing candidates. */
    public CompletionStage<ClaimResult> reserve(String hypervisorId, SchedulingRequest request) {
        Objects.requireNonNull(hypervisorId, "hypervisorId");
        Objects.requireNonNull(request, "request");
        CompletionStage<ClaimResult> reservation;
        try {
            reservation = allocationClaimer.tryReserve(hypervisorId, request);
        } catch (RuntimeException error) {
            reservation = CompletableFuture.failedFuture(error);
        }
        return reservation.handle((claim, error) -> {
            ClaimResult result = error == null && claim != null ? claim
                    : ClaimResult.unknown(request.reservationId(),
                            error == null ? "reservation returned no result" : error.toString());
            if (result.status() == ClaimResult.Status.ACCEPTED) metrics.success();
            log.info("scheduler claim completed hypervisorId={} requestId={} reservationId={} "
                            + "instanceId={} outcome={} detail={}", hypervisorId, request.requestId(),
                    request.reservationId(), request.instanceId(), result.status(), result.detail());
            return result;
        });
    }

    public List<ScoredCandidate> rank(List<HypervisorCandidate> candidates, SchedulingRequest request) {
        Comparator<ScoredCandidate> order = Comparator.comparingDouble(ScoredCandidate::score).reversed();
        if (request.resources().gpuRequest() > 0) {
            // Pack GPU requests into the smallest sufficient remainder, including outstanding reservations.
            // CPU/memory scores only break ties and cannot override GPU packing.
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

    public record WeightedScorer(CandidateScorer scorer, double weight) {
        public WeightedScorer {
            Objects.requireNonNull(scorer);
            if (weight < 0) throw new IllegalArgumentException("weight must be non-negative");
        }
    }
}
