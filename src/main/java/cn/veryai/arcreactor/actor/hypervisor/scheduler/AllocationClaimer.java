package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.ClaimResult;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;
import java.util.concurrent.CompletionStage;

public interface AllocationClaimer {
    CompletionStage<ClaimResult> tryReserve(HypervisorCandidate candidate, SchedulingRequest request);
}
