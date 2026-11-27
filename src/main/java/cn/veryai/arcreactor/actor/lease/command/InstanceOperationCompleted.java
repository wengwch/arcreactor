package cn.veryai.arcreactor.actor.lease.command;

public record InstanceOperationCompleted(String pollId, String error) implements LeaseCommand {}
