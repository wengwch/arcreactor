package cn.veryai.arcreactor.actor.lease.model;

import cn.veryai.arcreactor.actor.lease.model.LeaseState.PurchasePhase;
import java.io.Serializable;
import java.math.BigDecimal;

/** amount is zero until the quoted price is persisted. */
public record LeasePurchase(String orderId, long periodSeq, RentalPlan plan,
    BigDecimal amount, long planRevision, PurchasePhase phase) implements Serializable {

  public LeasePurchase priced(BigDecimal quotedAmount) {
    return new LeasePurchase(orderId, periodSeq, plan, quotedAmount, planRevision, phase);
  }

  public LeasePurchase locked() {
    return new LeasePurchase(orderId, periodSeq, plan,
        amount, planRevision, PurchasePhase.WAITING_ACTIVATION);
  }
}
