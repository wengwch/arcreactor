package cn.veryai.arcreactor.actor.lease.event;

import java.time.Instant;

public record LeaseTerminationRequested(Instant terminatedAt, String reason) implements LeaseEvent {}

