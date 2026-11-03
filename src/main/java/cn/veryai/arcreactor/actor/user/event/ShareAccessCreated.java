package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;

/** Records the access rule ID before waiting for ACTIVE and its access key. */
public record ShareAccessCreated(String shareId, ShareSpec spec, String osShareId,
    String osExportLocationId, String exportLocationPath, String osGrantAccessId) implements UserEvent {}
