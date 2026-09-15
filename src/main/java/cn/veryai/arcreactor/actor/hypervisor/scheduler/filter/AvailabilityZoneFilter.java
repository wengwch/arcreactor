package cn.veryai.arcreactor.actor.hypervisor.scheduler.filter;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;

public final class AvailabilityZoneFilter implements SchedulerFilter {
    @Override public boolean matches(SchedulingRequest request, HypervisorCandidate candidate) {
        String required = request.constraints().availabilityZone();
        return required == null || required.isBlank() || required.equals(candidate.availabilityZone());
    }
}
