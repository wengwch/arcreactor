package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.hypervisor.HypervisorPlacement;

public record SchedulingCompleted(long generation, String requestToken, HypervisorPlacement.ScheduleResult result,
    String failure) implements WorkflowCommand {}
