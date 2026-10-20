package cn.veryai.arcreactor.actor.instance.command;

public record SharedNetworkStatusCompleted(long generation, String networkId, String subnetId,
    boolean rejected, String detail) implements WorkflowCommand {}
