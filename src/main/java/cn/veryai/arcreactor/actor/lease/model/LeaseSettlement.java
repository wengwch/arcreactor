package cn.veryai.arcreactor.actor.lease.model;

import java.io.Serializable;
import java.math.BigDecimal;

/** amount is the order's price (zero until confirmed); deductAmount is consumption at the saved cutoff. */
public record LeaseSettlement(String orderId, String intentId, BigDecimal amount, BigDecimal deductAmount)
    implements Serializable {}
