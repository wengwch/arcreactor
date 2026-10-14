package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.UserShareState;

/** Records each operation intent and result, including IDs before the next external call. */
public record ShareUpdated(UserShareState share) implements UserEvent {}
