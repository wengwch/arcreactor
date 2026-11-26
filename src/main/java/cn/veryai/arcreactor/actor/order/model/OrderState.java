package cn.veryai.arcreactor.actor.order.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

public record OrderState(String orderId, String userId, String leaseId,
    BigDecimal amount, Phase phase, BigDecimal deductAmount, String error) implements Serializable {

  public enum Phase { NEW, LOCKING, LOCKED, DEDUCTING, SETTLED, INSUFFICIENT_FUNDS }

  public static OrderState empty(String orderId) {
    return new OrderState(orderId, null, null,
        BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), Phase.NEW, null, null);
  }

  public OrderState created(String nextUserId, String nextLeaseId, BigDecimal nextAmount) {
    return new OrderState(orderId, nextUserId, nextLeaseId, nextAmount, Phase.LOCKING, null, null);
  }

  public OrderState locked() {
    return new OrderState(orderId, userId, leaseId, amount, Phase.LOCKED, deductAmount, null);
  }

  public OrderState deductionRequested(BigDecimal requestedAmount) {
    return new OrderState(orderId, userId, leaseId, amount, Phase.DEDUCTING, requestedAmount, null);
  }

  public OrderState deducted() {
    return new OrderState(orderId, userId, leaseId, amount, Phase.SETTLED, deductAmount, null);
  }

  public OrderState insufficientFunds() {
    return new OrderState(orderId, userId, leaseId, amount,
        Phase.INSUFFICIENT_FUNDS, deductAmount, "INSUFFICIENT_FUNDS");
  }

  public OrderState withError(String nextError) {
    return new OrderState(orderId, userId, leaseId, amount, phase, deductAmount, nextError);
  }

  public boolean initialized() {
    return phase != Phase.NEW;
  }

  public boolean hasPendingWork() {
    return phase == Phase.LOCKING || phase == Phase.DEDUCTING;
  }
}
