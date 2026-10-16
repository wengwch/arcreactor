package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.UserProjectState;

public record ProjectUpdated(UserProjectState project) implements UserEvent {}
