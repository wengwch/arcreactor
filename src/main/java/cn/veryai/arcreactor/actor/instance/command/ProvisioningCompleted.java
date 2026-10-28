package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.openstack.OpenStackClient;

/** A null server without an error means a successful lookup found no server. */
public record ProvisioningCompleted(long generation, String requestToken, OpenStackClient.NovaServer server,
    boolean rejected, String detail) implements WorkflowCommand {}
