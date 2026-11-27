package cn.veryai.arcreactor.actor.lease.event;

/** Accepts renewal before checking the instance or settling the previous period. */
public record RenewalStarted() implements LeaseEvent {}
