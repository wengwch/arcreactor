package cn.veryai.arcreactor.actor.lease.event;

import java.math.BigDecimal;

public record PurchaseLocked(String orderId, BigDecimal amount) implements LeaseEvent {}
