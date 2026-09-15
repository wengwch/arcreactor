package cn.veryai.arcreactor.actor.hypervisor.scheduler.scorer;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;

public final class GpuFragmentationScorer implements CandidateScorer {
    @Override public double score(SchedulingRequest request, HypervisorCandidate candidate) {
        int requested = request.resources().gpuRequest().count();
        if (requested == 0) return 0.5;
        if (candidate.availableGpuCount() == 0) return 0;
        return (double) requested / candidate.availableGpuCount();
    }
}
