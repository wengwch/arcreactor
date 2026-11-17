package cn.veryai.arcreactor.actor.hypervisor.scheduler.filter;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement.ScheduleRequest;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;

public final class CapacityFilter implements SchedulerFilter {
    @Override public boolean matches(ScheduleRequest request, HypervisorCandidate candidate) {
        return candidate.availableVcpus() >= request.resources().vcpus()
                && candidate.availableMemoryGb() >= request.resources().memoryGb()
                && candidate.availableDiskGb() >= request.resources().diskGb();
    }
}
