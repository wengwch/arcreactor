package cn.veryai.arcreactor.actor.order.command;

import cn.veryai.arcreactor.actor.order.model.OrderState;
import java.math.BigDecimal;
import org.apache.pekko.actor.typed.ActorRef;

/** amount=0 requires deductAmount=0: settle an unopened order, or return its existing price for calculation. */
public record SettleOrder(String userId, String leaseId, String intentId, BigDecimal amount,
    BigDecimal deductAmount, ActorRef<OrderState> replyTo) implements OrderCommand {}
