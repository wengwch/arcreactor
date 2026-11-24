package cn.veryai.arcreactor.actor.lease.event;

import cn.veryai.arcreactor.actor.lease.model.RentalPlan;

public record RenewalPlanChanged(RentalPlan plan, long planRevision) implements LeaseEvent {}

