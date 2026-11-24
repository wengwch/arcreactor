package cn.veryai.arcreactor.actor.order.command;

/** Actor timer tick; queries unfinished payment, closure and refund operations. */
public record PollOrderProgress() implements OrderCommand {}
