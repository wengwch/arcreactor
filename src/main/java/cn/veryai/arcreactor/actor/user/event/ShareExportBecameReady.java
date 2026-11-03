package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;

/** Confirms the usable export location before submitting the access request. */
public record ShareExportBecameReady(String shareId, ShareSpec spec, String osShareId,
    String osExportLocationId, String exportLocationPath) implements UserEvent {}
