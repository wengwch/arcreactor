package cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;

public interface CandidateScorer {
    double score(SchedulingRequest request, HypervisorCandidate candidate);
}
