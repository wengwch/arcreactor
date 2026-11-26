package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.InstanceServerOperation;
import cn.veryai.arcreactor.openstack.OpenStackClient;
import java.time.Instant;

public record InstanceServerOperationFailed(String instanceId, InstanceServerOperation operation,
    OpenStackClient.NovaServer.Status novaStatus, String failureCode, Instant failedAt) implements InstanceWorkflowEvent {}
