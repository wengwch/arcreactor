package cn.veryai.arcreactor.actor.order.event;

import java.math.BigDecimal;

public record OrderCreated(String userId, String leaseId, String intentId, BigDecimal amount) implements OrderEvent {}
