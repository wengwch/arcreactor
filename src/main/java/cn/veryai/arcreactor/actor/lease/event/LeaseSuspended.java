package cn.veryai.arcreactor.actor.lease.event;

/** Stops billing and retains the instance; any settlement is persisted in the same batch. */
public record LeaseSuspended() implements LeaseEvent {}
