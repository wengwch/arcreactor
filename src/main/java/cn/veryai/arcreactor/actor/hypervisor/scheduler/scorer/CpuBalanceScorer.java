package cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;

public final class CpuBalanceScorer implements CandidateScorer {
    @Override public double score(SchedulingRequest request, HypervisorCandidate candidate) {
        if (candidate.totalVcpus() == 0) return 0;
        double remaining = candidate.availableVcpus() - request.resources().vcpus();
        return 1.0 - remaining / candidate.totalVcpus();
    }
}
