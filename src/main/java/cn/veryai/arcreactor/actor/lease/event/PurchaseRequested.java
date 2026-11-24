package cn.veryai.arcreactor.actor.lease.event;

import cn.veryai.arcreactor.actor.lease.model.LeasePurchase;

public record PurchaseRequested(LeasePurchase purchase) implements LeaseEvent {}

