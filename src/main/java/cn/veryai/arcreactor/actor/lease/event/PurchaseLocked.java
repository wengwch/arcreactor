package cn.veryai.arcreactor.actor.lease.event;

public record PurchaseLocked(String orderId) implements LeaseEvent {}
