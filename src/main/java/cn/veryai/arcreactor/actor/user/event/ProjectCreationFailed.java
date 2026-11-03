package cn.veryai.arcreactor.actor.user.event;

/** Records the terminal failure of the project creation workflow. */
public record ProjectCreationFailed(String clusterId, String failure) implements UserEvent {}
