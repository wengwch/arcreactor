package cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement.ScheduleRequest;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;

public final class MemoryBalanceScorer implements CandidateScorer {
    @Override public double score(ScheduleRequest request, HypervisorCandidate candidate) {
        if (candidate.totalMemoryGb() == 0) return 0;
        double remaining = candidate.availableMemoryGb() - request.resources().memoryGb();
        return 1.0 - remaining / candidate.totalMemoryGb();
    }
}
