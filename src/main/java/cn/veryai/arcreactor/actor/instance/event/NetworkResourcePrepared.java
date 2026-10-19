package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.NetworkPreparation;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;

public record NetworkResourcePrepared(String instanceId, NetworkPreparation.Step step,
    PreparedNetwork resources) implements InstanceWorkflowEvent {}
