package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import java.io.Serializable;
import java.util.List;

public record SchedulingResult(List<String> hypervisorIds, String detail) implements Serializable {
    public SchedulingResult {
        hypervisorIds = List.copyOf(hypervisorIds);
    }
}
