package cn.veryai.arcreactor.actor.lease.event;

public record SettlementCompleted(String orderId) implements LeaseEvent {}
