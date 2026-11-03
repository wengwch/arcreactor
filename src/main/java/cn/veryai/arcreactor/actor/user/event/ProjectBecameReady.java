package cn.veryai.arcreactor.actor.user.event;

/** Confirms that the project exists and its administrator role has been granted. */
public record ProjectBecameReady(String clusterId, String projectId) implements UserEvent {}
