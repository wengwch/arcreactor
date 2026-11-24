package cn.veryai.arcreactor.actor.lease.event;

import java.time.Instant;

public record LeaseReconciliationScheduled(Instant nextActionAt) implements LeaseEvent {}

