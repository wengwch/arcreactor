package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.UserNetworkState;

public record NetworkUpdated(UserNetworkState network) implements UserEvent {}
