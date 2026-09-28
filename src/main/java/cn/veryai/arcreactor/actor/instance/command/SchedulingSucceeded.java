package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import java.util.Objects;

public record SchedulingSucceeded(HypervisorPlacement.ScheduleResult result) implements WorkflowCommand {
    public SchedulingSucceeded { Objects.requireNonNull(result, "result"); }
}
