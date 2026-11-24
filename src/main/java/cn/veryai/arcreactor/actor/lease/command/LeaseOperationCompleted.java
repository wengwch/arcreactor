package cn.veryai.arcreactor.actor.lease.command;

import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.order.model.OrderState;

public record LeaseOperationCompleted(String key, String token, OrderState order, InstanceWorkflowState instance,
        String error) implements LeaseCommand {}
