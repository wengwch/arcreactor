package cn.veryai.arcreactor.actor.lease.event;

public record LeaseRetryScheduled(String error) implements LeaseEvent {}

