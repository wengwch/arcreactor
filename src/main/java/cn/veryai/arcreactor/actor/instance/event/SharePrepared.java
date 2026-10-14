package cn.veryai.arcreactor.actor.instance.event;

import cn.veryai.arcreactor.actor.instance.model.PreparedShare;
import java.time.Instant;

public record SharePrepared(String instanceId, PreparedShare share, Instant preparedAt)
    implements InstanceWorkflowEvent {}
