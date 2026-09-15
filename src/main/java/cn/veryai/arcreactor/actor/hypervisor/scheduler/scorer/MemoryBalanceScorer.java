package cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;

public final class MemoryBalanceScorer implements CandidateScorer {
    @Override public double score(SchedulingRequest request, HypervisorCandidate candidate) {
        if (candidate.totalMemoryMb() == 0) return 0;
        double remaining = candidate.availableMemoryMb() - request.resources().memoryMb();
        return 1.0 - remaining / candidate.totalMemoryMb();
    }
}
