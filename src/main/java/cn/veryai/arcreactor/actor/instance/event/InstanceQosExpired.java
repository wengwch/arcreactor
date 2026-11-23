package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;

public record InstanceQosExpired(String instanceId, InstanceQosUpdate update,
    PreparedNetwork resources) implements InstanceWorkflowEvent {}
