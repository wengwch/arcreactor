package cn.veryai.arcreactor.actor.lease.model;

import java.io.Serializable;
import java.math.BigDecimal;

/** amount is the confirmed order price; deductAmount is consumption at the saved cutoff. */
public record LeaseSettlement(String orderId, BigDecimal amount, BigDecimal deductAmount)
    implements Serializable {}
