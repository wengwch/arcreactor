package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record SshConnectivityVerified(String instanceId, Instant verifiedAt) implements InstanceWorkflowEvent {
}
