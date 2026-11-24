package cn.veryai.arcreactor.actor.order.command;

import cn.veryai.arcreactor.actor.order.model.OrderState;
import org.apache.pekko.actor.typed.ActorRef;

public record SettleOrder(String userId, String leaseId, String intentId, long amount,
    long deductAmount, ActorRef<OrderState> replyTo) implements OrderCommand {}
