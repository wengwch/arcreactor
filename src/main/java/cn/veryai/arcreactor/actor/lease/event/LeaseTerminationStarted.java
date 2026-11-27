package cn.veryai.arcreactor.actor.lease.event;

import java.time.Instant;

public record LeaseTerminationStarted(Instant terminatedAt) implements LeaseEvent {}
