package cn.veryai.arcreactor.actor.order.event;

public record OrderCreated(String userId, String leaseId, String intentId, long amount) implements OrderEvent {}
