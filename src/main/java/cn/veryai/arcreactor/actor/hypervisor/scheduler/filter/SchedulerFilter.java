package cn.veryai.arcreactor.actor.hypervisor.scheduler.filter;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;

public interface SchedulerFilter {
    boolean matches(SchedulingRequest request, HypervisorCandidate candidate);
}
