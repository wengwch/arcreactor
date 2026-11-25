package cn.veryai.arcreactor.actor.lease.event;

import cn.veryai.arcreactor.actor.lease.model.LeaseState.PurchasePhase;
import java.math.BigDecimal;

public record PurchasePhaseChanged(String orderId, PurchasePhase phase, BigDecimal orderAmount) implements LeaseEvent {
  public PurchasePhaseChanged(String orderId, PurchasePhase phase) {
    this(orderId, phase, null);
  }
}

