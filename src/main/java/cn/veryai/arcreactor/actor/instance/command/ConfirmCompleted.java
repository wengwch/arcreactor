package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;

public record ConfirmCompleted(long generation, String requestToken, int retryCount,
    HypervisorPlacement.ReservationResult result) implements WorkflowCommand {}
