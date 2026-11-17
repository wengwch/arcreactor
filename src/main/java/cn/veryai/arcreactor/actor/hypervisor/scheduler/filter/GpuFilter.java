package cn.veryai.arcreactor.actor.hypervisor.scheduler.filter;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement.ScheduleRequest;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;

public final class GpuFilter implements SchedulerFilter {
    @Override public boolean matches(ScheduleRequest request, HypervisorCandidate candidate) {
        return candidate.availableGpuCount() >= request.resources().gpuCount();
    }
}
