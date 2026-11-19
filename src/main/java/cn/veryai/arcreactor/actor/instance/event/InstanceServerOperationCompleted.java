package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceServerOperation;

public record InstanceServerOperationCompleted(String instanceId, InstanceServerOperation operation,
    String osImageId) implements InstanceWorkflowEvent {}
