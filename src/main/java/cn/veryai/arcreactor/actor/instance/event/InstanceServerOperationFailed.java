package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceServerOperation;
import cn.veryai.arcreactor.openstack.OpenStackClient;

public record InstanceServerOperationFailed(String instanceId, InstanceServerOperation operation,
    OpenStackClient.NovaServer.Status novaStatus, String failureCode) implements InstanceWorkflowEvent {}
