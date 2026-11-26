package cn.veryai.arcreactor.actor.order.command;

import cn.veryai.arcreactor.actor.order.model.OrderState;
import java.math.BigDecimal;
import org.apache.pekko.actor.typed.ActorRef;

/** Settles a locked order at its confirmed price; deductAmount may be zero. */
public record SettleOrder(String userId, String leaseId, BigDecimal amount,
    BigDecimal deductAmount, ActorRef<OrderState> replyTo) implements OrderCommand {}
