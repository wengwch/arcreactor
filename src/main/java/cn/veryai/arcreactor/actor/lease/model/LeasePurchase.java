package cn.veryai.arcreactor.actor.lease.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/** amount is zero until priced; a null startsAt starts billing when the instance is confirmed active. */
public record LeasePurchase(String orderId, long periodSeq, RentalPlan plan,
    BigDecimal amount, long planRevision, Instant startsAt) implements Serializable {

  public LeasePurchase priced(BigDecimal quotedAmount) {
    return new LeasePurchase(orderId, periodSeq, plan, quotedAmount, planRevision, startsAt);
  }
}
