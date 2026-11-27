package cn.veryai.arcreactor.actor.lease.model;

import java.io.Serializable;
import java.math.BigDecimal;

/** amount is zero until the quoted price is persisted. */
public record LeasePurchase(String orderId, long periodSeq, RentalPlan plan,
    BigDecimal amount, long planRevision) implements Serializable {

  public LeasePurchase priced(BigDecimal quotedAmount) {
    return new LeasePurchase(orderId, periodSeq, plan, quotedAmount, planRevision);
  }
}
