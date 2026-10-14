package cn.veryai.arcreactor.actor.instance.command;

import java.time.Instant;

public record ForwardingPreparationTimedOut(Instant deadline) implements WorkflowCommand {}
