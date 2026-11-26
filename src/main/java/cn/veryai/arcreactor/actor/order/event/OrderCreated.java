package cn.veryai.arcreactor.actor.order.event;

import java.math.BigDecimal;

public record OrderCreated(String userId, String leaseId, BigDecimal amount) implements OrderEvent {}
