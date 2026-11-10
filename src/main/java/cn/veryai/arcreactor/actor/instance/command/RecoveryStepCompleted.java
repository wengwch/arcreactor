package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.InstanceRecovery;
import cn.veryai.arcreactor.actor.instance.model.InstancePhase;

/** Local pipeToSelf result; recovery decisions are persisted by the instance actor. */
public record RecoveryStepCompleted(InstancePhase phase, long generation, String requestToken,
    int attempt, InstanceRecovery.Outcome outcome, String failure) implements WorkflowCommand {}
