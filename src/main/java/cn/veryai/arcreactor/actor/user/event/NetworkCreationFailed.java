package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.NetworkSpec;

/** Includes the request so the failed network can be projected independently. */
public record NetworkCreationFailed(NetworkSpec spec, String osProjectId, String failure) implements UserEvent {}
