package cn.veryai.arcreactor.actor.lease.event;

import java.time.Instant;

public record LeaseTerminationPrepared(Instant terminatedAt, String reason) implements LeaseEvent {}

