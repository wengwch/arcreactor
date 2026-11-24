package cn.veryai.arcreactor.actor.lease.command;

/** Polls pending orders and instance progress; lease expiry and automatic renewal remain external. */
public record PollLeaseProgress() implements LeaseCommand {}
