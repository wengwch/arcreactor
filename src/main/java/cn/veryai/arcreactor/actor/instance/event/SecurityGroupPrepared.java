package cn.veryai.arcreactor.actor.instance.event;

public record SecurityGroupPrepared(String instanceId, String securityGroupId) implements InstanceWorkflowEvent {}
