package cn.veryai.arcreactor.actor.user.event;

/** Includes the requested name so the ready project can be projected independently. */
public record ProjectBecameReady(String clusterId, String name, String osProjectId) implements UserEvent {}
