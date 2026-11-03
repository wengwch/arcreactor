package cn.veryai.arcreactor.actor.user.event;

/** Records the creation request before calling OpenStack. */
public record ProjectCreationRequested(String clusterId, String name) implements UserEvent {}
