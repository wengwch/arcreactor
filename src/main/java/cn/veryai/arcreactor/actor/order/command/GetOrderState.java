package cn.veryai.arcreactor.actor.order.command;

import cn.veryai.arcreactor.actor.order.model.OrderState;
import org.apache.pekko.actor.typed.ActorRef;

public record GetOrderState(ActorRef<OrderState> replyTo) implements OrderCommand {}
