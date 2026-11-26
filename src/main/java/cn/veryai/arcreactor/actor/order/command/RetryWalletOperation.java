package cn.veryai.arcreactor.actor.order.command;

/** One-shot retry of the order's pending lock or deduction. */
public record RetryWalletOperation() implements OrderCommand {}
