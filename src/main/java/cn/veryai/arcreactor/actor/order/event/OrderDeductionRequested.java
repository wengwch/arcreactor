package cn.veryai.arcreactor.actor.order.event;

public record OrderDeductionRequested(long amount) implements OrderEvent {}
