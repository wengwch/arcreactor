package cn.veryai.arcreactor.actor.lease.event;

public record AutoRenewConfigured(boolean enabled) implements LeaseEvent {}

