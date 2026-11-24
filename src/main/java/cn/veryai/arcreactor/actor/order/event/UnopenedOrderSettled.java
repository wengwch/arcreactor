package cn.veryai.arcreactor.actor.order.event;

public record UnopenedOrderSettled(String userId, String leaseId, String intentId, long amount) implements OrderEvent {}
