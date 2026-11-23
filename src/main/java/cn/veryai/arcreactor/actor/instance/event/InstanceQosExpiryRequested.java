package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;

public record InstanceQosExpiryRequested(String instanceId, InstanceQosUpdate update) implements InstanceWorkflowEvent {}
