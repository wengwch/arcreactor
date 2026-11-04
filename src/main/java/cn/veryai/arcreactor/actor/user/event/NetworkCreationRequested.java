package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.NetworkSpec;
import java.time.Instant;

/** Records the creation request before calling OpenStack. */
public record NetworkCreationRequested(NetworkSpec spec, String projectId, Instant deadline) implements UserEvent {}
