package cn.veryai.arcreactor.actor.lease.event;

import cn.veryai.arcreactor.actor.lease.model.LeaseSettlement;

public record SettlementRequested(LeaseSettlement settlement) implements LeaseEvent {}
