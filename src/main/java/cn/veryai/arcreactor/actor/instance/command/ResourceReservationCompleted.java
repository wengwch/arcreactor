package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;
import java.util.Objects;

public record ResourceReservationCompleted(long generation, String requestToken,
        HypervisorPlacement.ReserveResult result) implements WorkflowCommand {
    public ResourceReservationCompleted {
        Objects.requireNonNull(result, "result");
    }
}
