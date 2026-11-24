package cn.veryai.arcreactor.actor.lease.event;

import java.time.Instant;

public record PeriodEnded(Instant endsAt) implements LeaseEvent {}

