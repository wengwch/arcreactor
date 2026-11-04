package cn.veryai.arcreactor.actor.instance.command;

import java.time.Instant;

public record SecurityGroupPreparationTimedOut(Instant deadline) implements WorkflowCommand {}
