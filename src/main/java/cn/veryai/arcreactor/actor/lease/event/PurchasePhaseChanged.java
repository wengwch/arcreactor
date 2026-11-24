package cn.veryai.arcreactor.actor.lease.event;

import cn.veryai.arcreactor.actor.lease.model.LeaseState.PurchasePhase;

public record PurchasePhaseChanged(String orderId, PurchasePhase phase) implements LeaseEvent {}

