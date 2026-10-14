package cn.veryai.arcreactor.actor.instance.command;

import java.time.Instant;

public record SharePreparationTimedOut(Instant deadline) implements WorkflowCommand {}
