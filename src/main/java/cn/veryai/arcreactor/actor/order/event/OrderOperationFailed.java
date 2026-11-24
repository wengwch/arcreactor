package cn.veryai.arcreactor.actor.order.event;

public record OrderOperationFailed(String error) implements OrderEvent {}
