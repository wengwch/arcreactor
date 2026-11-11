package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;

public record SubPortPreparationCompleted(long generation, String requestToken, PreparedNetwork resources,
    String failure) implements WorkflowCommand {}
