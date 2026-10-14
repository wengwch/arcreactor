package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record ForwardingPreparationStarted(String instanceId, Instant deadline) implements InstanceWorkflowEvent {}
