package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import java.time.Instant;

public record ForwardingPrepared(String instanceId, PreparedForwarding forwarding, Instant preparedAt)
    implements InstanceWorkflowEvent {}
