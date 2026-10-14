package cn.veryai.arcreactor.actor.instance.event;

public record ForwardingStatusCheckStarted(String instanceId) implements InstanceWorkflowEvent {}
