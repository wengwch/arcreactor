package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;

/** The creation intent is persisted before contacting OpenStack. */
public record ForwardingCreationRequested(FloatingIpIdentity identity, String claimId, ForwardingSpec spec)
    implements FloatingIpEvent {}
