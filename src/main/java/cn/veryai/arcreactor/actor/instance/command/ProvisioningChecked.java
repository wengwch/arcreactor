package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.openstack.OpenStackClient;

/** A failed Nova lookup has no server; rejected marks a terminal lookup failure. SSH failures retain the observed server. */
public record ProvisioningChecked(long generation, String requestToken, int attempt, OpenStackClient.NovaServer server,
    boolean sshConnected, boolean rejected, String failure) implements WorkflowCommand {}
