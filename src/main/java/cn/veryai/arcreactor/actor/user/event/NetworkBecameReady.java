package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.NetworkResources;
import cn.veryai.arcreactor.actor.user.model.NetworkSpec;

/** Includes the request and all resource IDs so readiness can be projected independently. */
public record NetworkBecameReady(NetworkSpec spec, String projectId, NetworkResources resources)
    implements UserEvent {}
