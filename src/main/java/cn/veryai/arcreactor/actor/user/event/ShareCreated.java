package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;

/** Manila accepted creation; the share and export location are not necessarily ready. */
public record ShareCreated(String shareId, ShareSpec spec, String osShareId) implements UserEvent {}
