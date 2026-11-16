package cn.veryai.arcreactor.actor.user.event;

public record UserCleanupFailed(String resourceId, String failure) implements UserEvent {}
