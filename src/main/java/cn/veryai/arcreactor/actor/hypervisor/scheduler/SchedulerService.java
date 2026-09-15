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
        return candidateFinder.find(request).thenCompose(candidates -> {
            List<ScoredCandidate> ranked = rank(candidates, request);
            log.info("scheduler ranked requestId={} instanceId={} candidates={} eligible={}",
                    request.requestId(), request.instanceId(), candidates.size(), ranked.size());
            return tryCandidates(ranked, request, 0, 0, false);
        });
    }

    public List<ScoredCandidate> rank(List<HypervisorCandidate> candidates, SchedulingRequest request) {
        return candidates.stream()
                .filter(candidate -> filters.stream().allMatch(filter -> filter.matches(request, candidate)))
                .map(candidate -> new ScoredCandidate(candidate, scorers.stream()
                        .mapToDouble(weighted -> weighted.weight() * weighted.scorer().score(request, candidate))
                        .sum()))
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(scored -> scored.candidate().hypervisorId()))
                .toList();
    }

    private CompletionStage<SchedulingResult> tryCandidates(List<ScoredCandidate> ranked,
            SchedulingRequest request, int index, int attempts, boolean timeoutRetried) {
        if (index >= ranked.size() || attempts >= maxClaimAttempts) {
            metrics.noValidHost();
            return CompletableFuture.completedFuture(SchedulingResult.noValidHost(
                    request.reservationId(), attempts, "all candidates rejected or unavailable"));
        }
        HypervisorCandidate candidate = ranked.get(index).candidate();
        log.info("scheduler candidate selected hypervisorId={} requestId={} reservationId={} "
                        + "instanceId={} score={}", candidate.hypervisorId(), request.requestId(),
                request.reservationId(), request.instanceId(), ranked.get(index).score());
        return allocationClaimer.tryReserve(candidate, request)
                .exceptionally(error -> ClaimResult.failed(request.reservationId(), error.toString()))
                .thenCompose(claim -> {
                    int nextAttempts = attempts + 1;
                    if (claim.status() == ClaimResult.Status.ACCEPTED) {
                        metrics.success();
                        log.info("scheduler claim accepted hypervisorId={} requestId={} reservationId={} "
                                        + "instanceId={}", candidate.hypervisorId(), request.requestId(),
                                request.reservationId(), request.instanceId());
                        return CompletableFuture.completedFuture(SchedulingResult.reserved(
                                candidate.hypervisorId(), request.reservationId(), nextAttempts));
                    }
                    log.info("scheduler claim rejected hypervisorId={} requestId={} reservationId={} "
                                    + "instanceId={} outcome={} detail={}", candidate.hypervisorId(),
                            request.requestId(), request.reservationId(), request.instanceId(),
                            claim.status(), claim.detail());
                    if (claim.status() == ClaimResult.Status.TIMEOUT && !timeoutRetried
                            && nextAttempts < maxClaimAttempts) {
                        metrics.claimRetry();
                        return tryCandidates(ranked, request, index, nextAttempts, true);
                    }
                    if (claim.status() == ClaimResult.Status.TIMEOUT || claim.status() == ClaimResult.Status.FAILED) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "Reservation outcome is unknown on host " + candidate.hypervisorId()
                                        + "; stop scheduling to avoid reserving a second host"));
                    }
                    metrics.claimRetry();
                    return tryCandidates(ranked, request, index + 1, nextAttempts, false);
                });
    }

    public record WeightedScorer(CandidateScorer scorer, double weight) {
        public WeightedScorer {
            Objects.requireNonNull(scorer);
            if (weight < 0) throw new IllegalArgumentException("weight must be non-negative");
        }
    }
}
