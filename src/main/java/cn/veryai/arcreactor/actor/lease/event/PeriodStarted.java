package cn.veryai.arcreactor.actor.lease.event;

import cn.veryai.arcreactor.actor.lease.model.LeasePeriod;

public record PeriodStarted(LeasePeriod period) implements LeaseEvent {}

