package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;

public record InstanceQosExpiryFailed(String instanceId, InstanceQosUpdate update) implements InstanceWorkflowEvent {}
