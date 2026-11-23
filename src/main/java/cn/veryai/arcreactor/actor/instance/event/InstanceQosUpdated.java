package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceQosUpdate;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import java.time.Instant;

/** Confirms activation and records the exact logical switch used by downstream settlement. */
public record InstanceQosUpdated(String instanceId, InstanceQosUpdate update,
    PreparedNetwork resources, Instant switchedAt, String replacedQosId) implements InstanceWorkflowEvent {}

