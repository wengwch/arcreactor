package cn.veryai.arcreactor.actor.lease.model;

import cn.veryai.arcreactor.actor.lease.model.LeaseState.PurchasePhase;
import java.io.Serializable;
import java.math.BigDecimal;

/** amount is zero until the order's lock is confirmed. */
public record LeasePurchase(String orderId, long periodSeq, RentalPlan plan,
    BigDecimal amount, long planRevision, PurchasePhase phase) implements Serializable {

  public LeasePurchase locked(BigDecimal orderAmount) {
    return new LeasePurchase(orderId, periodSeq, plan,
        orderAmount, planRevision, PurchasePhase.WAITING_ACTIVATION);
  }
}
