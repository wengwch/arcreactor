package cn.veryai.arcreactor.actor.hypervisor.scheduler;

import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.HypervisorCandidate;
import cn.veryai.arcreactor.actor.hypervisor.scheduler.model.SchedulingRequest;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface CandidateFinder {
    CompletionStage<List<HypervisorCandidate>> find(SchedulingRequest request);
}
