package cn.veryai.arcreactor.actor.lease.command;

public record LeasePriceQuoted(String orderId, String token, Long amount, String error) implements LeaseCommand {}
