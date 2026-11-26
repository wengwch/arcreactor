package cn.veryai.arcreactor.actor.lease.command;

import java.math.BigDecimal;

/** Returns a price quote to the lease before it decides whether to create the order. */
public record PriceQuoteCompleted(String orderId, String token, BigDecimal amount, String error) implements LeaseCommand {}
