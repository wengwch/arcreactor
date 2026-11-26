package cn.veryai.arcreactor.actor.lease.event;

import java.math.BigDecimal;

public record PurchasePriced(String orderId, BigDecimal amount) implements LeaseEvent {}
