package cn.veryai.arcreactor.actor.instance.event;

import java.time.Instant;

public record ProjectPrepared(String instanceId, String projectId, Instant preparedAt) implements InstanceWorkflowEvent {}
