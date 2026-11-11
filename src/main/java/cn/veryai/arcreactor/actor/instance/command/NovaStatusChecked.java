package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.openstack.OpenStackClient;

/** Result of querying a known Nova server; a null server without an error means it was not found. */
public record NovaStatusChecked(long generation, String requestToken, OpenStackClient.NovaServer server,
    String failure) implements WorkflowCommand {}
