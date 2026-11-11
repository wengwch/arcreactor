package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.openstack.OpenStackClient;

/** Result of one Nova creation request; failures are persisted without starting recovery. */
public record NovaCreationCompleted(long generation, String requestToken, OpenStackClient.NovaServer server,
    boolean rejected, String failure) implements WorkflowCommand {}
