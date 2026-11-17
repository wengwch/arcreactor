package cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement.ScheduleRequest;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;

public interface CandidateScorer {
    double score(ScheduleRequest request, HypervisorCandidate candidate);
}
