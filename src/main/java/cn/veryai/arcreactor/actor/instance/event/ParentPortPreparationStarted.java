package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record ParentPortPreparationStarted(String instanceId, Instant deadline) implements InstanceWorkflowEvent {}
