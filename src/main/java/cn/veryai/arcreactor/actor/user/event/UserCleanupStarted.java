package cn.veryai.arcreactor.actor.user.event;

public record UserCleanupStarted(long generation) implements UserEvent {}
