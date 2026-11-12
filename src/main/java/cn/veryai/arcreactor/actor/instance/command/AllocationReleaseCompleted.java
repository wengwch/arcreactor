package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;

public record AllocationReleaseCompleted(long generation, String requestToken,
        HypervisorPlacement.ReservationResult result, String failure) implements WorkflowCommand {
}
