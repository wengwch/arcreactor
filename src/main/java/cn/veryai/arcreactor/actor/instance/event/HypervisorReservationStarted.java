package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import java.time.Instant;
import java.util.List;

public record HypervisorReservationStarted(String instanceId, String hypervisorId, String hostname,
        List<HypervisorPlacement.Candidate> remainingHypervisors, Instant startedAt) implements InstanceWorkflowEvent {
    public HypervisorReservationStarted {
        remainingHypervisors = List.copyOf(remainingHypervisors);
    }
}
