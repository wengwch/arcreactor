package cn.veryai.arcreactor.actor.user.event;

/** Records the terminal failure of the network creation workflow. */
public record NetworkCreationFailed(String regionId, String failure) implements UserEvent {}
