package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.InstancePhase;

/** Internal timer message for the stage being recovered. */
public record RetryRecovery(InstancePhase phase, long generation, int attempt) implements WorkflowCommand {}
