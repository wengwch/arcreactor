package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;

public record TrunkPreparationCompleted(long generation, String requestToken, PreparedNetwork resources,
    boolean rejected, String detail) implements WorkflowCommand {}
