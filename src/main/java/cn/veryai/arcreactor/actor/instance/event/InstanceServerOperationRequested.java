package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceServerOperation;

public record InstanceServerOperationRequested(String instanceId, InstanceServerOperation operation)
    implements InstanceWorkflowEvent {}
