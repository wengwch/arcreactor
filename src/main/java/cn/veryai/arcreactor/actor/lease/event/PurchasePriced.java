package cn.veryai.arcreactor.actor.lease.event;

/** The amount in CNY cents is fixed before an order can be created. */
public record PurchasePriced(String orderId, long amount) implements LeaseEvent {}
