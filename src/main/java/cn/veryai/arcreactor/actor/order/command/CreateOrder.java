package cn.veryai.arcreactor.actor.order.command;

import cn.veryai.arcreactor.actor.order.model.OrderState;
import org.apache.pekko.actor.typed.ActorRef;

public record CreateOrder(String userId, String leaseId, String intentId, long amount,
    ActorRef<OrderState> replyTo) implements OrderCommand {}
