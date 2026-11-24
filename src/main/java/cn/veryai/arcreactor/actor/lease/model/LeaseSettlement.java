package cn.veryai.arcreactor.actor.lease.model;

import java.io.Serializable;

/** An immutable wallet settlement intent; amount is the order's price, deductAmount is consumption. */
public record LeaseSettlement(String orderId, String intentId, long amount, long deductAmount) implements Serializable {}
