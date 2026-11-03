package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;

/** Records the request before creating a Manila share. */
public record ShareCreationRequested(String shareId, ShareSpec spec) implements UserEvent {}
