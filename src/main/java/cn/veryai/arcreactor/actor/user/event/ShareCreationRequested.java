package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import java.time.Instant;

/** Records the request before creating a Manila share. */
public record ShareCreationRequested(String shareId, ShareSpec spec, Instant deadline) implements UserEvent {}
