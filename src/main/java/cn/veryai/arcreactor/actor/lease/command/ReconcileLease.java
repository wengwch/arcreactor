package cn.veryai.arcreactor.actor.lease.command;

/** External wake-up; the actor verifies its persisted deadline and current state before proceeding. */
public record ReconcileLease() implements LeaseCommand {}
