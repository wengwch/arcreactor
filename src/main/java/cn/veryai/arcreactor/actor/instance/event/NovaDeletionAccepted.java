package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

/** Nova accepted deletion; the server must still be queried until it disappears. */
public record NovaDeletionAccepted(String instanceId, String serverId, Instant acceptedAt)
        implements InstanceWorkflowEvent {
}
