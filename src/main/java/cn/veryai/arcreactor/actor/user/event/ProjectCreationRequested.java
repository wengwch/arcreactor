package cn.veryai.arcreactor.actor.user.event;

import java.time.Instant;

/** Records the creation request before calling OpenStack. */
public record ProjectCreationRequested(String clusterId, String name, Instant deadline) implements UserEvent {}
