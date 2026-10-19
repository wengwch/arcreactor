package cn.veryai.arcreactor.actor.instance.command;

import java.time.Instant;

public record ProjectPreparationTimedOut(Instant deadline) implements WorkflowCommand {}
