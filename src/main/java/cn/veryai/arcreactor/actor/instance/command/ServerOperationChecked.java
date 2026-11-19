package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.openstack.OpenStackClient;

public record ServerOperationChecked(String operationId, String requestToken, int attempt,
    OpenStackClient.NovaServer server, boolean sshConnected, boolean rejected, String failure)
    implements WorkflowCommand {}
