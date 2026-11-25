package cn.veryai.arcreactor.actor.order.event;

import java.math.BigDecimal;

public record OrderDeductionRequested(BigDecimal amount) implements OrderEvent {}
