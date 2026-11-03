package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;

/** Retains confirmed resource IDs so a failure can be projected independently. */
public record ShareCreationFailed(String shareId, ShareSpec spec, String osShareId,
    String osExportLocationId, String exportLocationPath, String osGrantAccessId,
    String failure) implements UserEvent {}
