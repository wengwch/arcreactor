package cn.veryai.arcreactor.actor.instance.command;

public record SecurityGroupPreparationCompleted(long generation, String requestToken, String securityGroupId,
    String failure) implements WorkflowCommand {}
