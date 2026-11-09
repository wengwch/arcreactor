package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record DependencyPreparationStarted(String instanceId, Instant deadline) implements InstanceWorkflowEvent {}
