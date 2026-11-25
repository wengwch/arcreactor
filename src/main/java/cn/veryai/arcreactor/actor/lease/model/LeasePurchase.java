package cn.veryai.arcreactor.actor.lease.model;

import cn.veryai.arcreactor.actor.lease.model.LeaseState.PurchasePhase;
import java.io.Serializable;
import java.math.BigDecimal;

/** amount is zero until the order's persisted price is confirmed. */
public record LeasePurchase(String orderId, String intentId, long periodSeq, RentalPlan plan,
    BigDecimal amount, long planRevision, PurchasePhase phase) implements Serializable {

  public LeasePurchase withPhase(PurchasePhase nextPhase, BigDecimal orderAmount) {
    return new LeasePurchase(orderId, intentId, periodSeq, plan,
        orderAmount == null ? amount : orderAmount, planRevision, nextPhase);
  }
}
