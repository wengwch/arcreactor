package cn.veryai.arcreactor.actor.hypervisor.scheduler.filter;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;

public final class GpuFilter implements SchedulerFilter {
    @Override public boolean matches(SchedulingRequest request, HypervisorCandidate candidate) {
        return candidate.availableGpuCount() >= request.resources().gpuRequest();
    }
}
