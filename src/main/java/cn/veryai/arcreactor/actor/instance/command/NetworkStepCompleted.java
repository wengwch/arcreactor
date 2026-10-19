package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;

public record NetworkStepCompleted(long generation, PreparedNetwork resources,
    boolean rejected, String detail) implements WorkflowCommand {}
