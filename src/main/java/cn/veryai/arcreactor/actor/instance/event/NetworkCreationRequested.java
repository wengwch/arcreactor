package cn.veryai.arcreactor.actor.instance.event;

public record NetworkCreationRequested(String instanceId) implements InstanceWorkflowEvent {}
