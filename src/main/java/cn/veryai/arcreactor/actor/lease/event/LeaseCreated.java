package cn.veryai.arcreactor.actor.lease.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.lease.model.RentalPlan;

public record LeaseCreated(String userId, String requestId, String fingerprint,
        InstanceSpec instanceSpec, RentalPlan plan, boolean autoRenew) implements LeaseEvent {}

