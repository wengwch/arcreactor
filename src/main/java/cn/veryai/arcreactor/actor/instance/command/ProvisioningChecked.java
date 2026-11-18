package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.openstack.OpenStackClient;

/** A failed Nova lookup has no server. An SSH failure retains the ACTIVE server that was observed. */
public record ProvisioningChecked(long generation, String requestToken, OpenStackClient.NovaServer server,
    boolean sshConnected, String failure) implements WorkflowCommand {}
