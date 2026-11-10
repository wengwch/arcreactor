package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.InstancePhase;
import java.time.Instant;

/** Internal deadline message for recovery of security groups and forwardings. */
public record RecoveryTimedOut(InstancePhase phase, long generation, Instant deadline) implements WorkflowCommand {}
