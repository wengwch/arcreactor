package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.NetworkSpec;

/** Records the creation request before calling OpenStack. */
public record NetworkCreationRequested(String clusterId, NetworkSpec spec, String osProjectId) implements UserEvent {}
