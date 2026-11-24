package cn.veryai.arcreactor.actor.lease.model;

import cn.veryai.arcreactor.actor.lease.model.LeaseState.PurchasePhase;
import java.io.Serializable;

public record LeasePurchase(String orderId, String intentId, long periodSeq, RentalPlan plan,
    long amount, long planRevision, PurchasePhase phase) implements Serializable {

  public LeasePurchase priced(long quotedAmount) {
    return new LeasePurchase(orderId, intentId, periodSeq, plan, quotedAmount, planRevision, PurchasePhase.CREATING_ORDER);
  }

  public LeasePurchase withPhase(PurchasePhase nextPhase) {
    return new LeasePurchase(orderId, intentId, periodSeq, plan, amount, planRevision, nextPhase);
  }
}
