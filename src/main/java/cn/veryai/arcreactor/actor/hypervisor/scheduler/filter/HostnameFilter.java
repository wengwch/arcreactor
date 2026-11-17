package cn.veryai.arcreactor.actor.hypervisor.scheduler.filter;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement.ScheduleRequest;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;

/** Restricts placement to the requested hostname when present. */
public final class HostnameFilter implements SchedulerFilter {
    @Override public boolean matches(ScheduleRequest request, HypervisorCandidate candidate) {
        String required = request.hostname();
        return required == null || required.equals(candidate.hostname());
    }
}
