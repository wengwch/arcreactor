package cn.veryai.arcreactor.actor.user.event;

/** Includes the requested name so the failed project can be projected independently. */
public record ProjectCreationFailed(String clusterId, String name, String failure) implements UserEvent {}
