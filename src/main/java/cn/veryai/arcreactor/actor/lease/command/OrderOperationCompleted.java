package cn.veryai.arcreactor.actor.lease.command;

import cn.veryai.arcreactor.actor.order.model.OrderState;

public record OrderOperationCompleted(String pollId, OrderState order, String error) implements LeaseCommand {}
