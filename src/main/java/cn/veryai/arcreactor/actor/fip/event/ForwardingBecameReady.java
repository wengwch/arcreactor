package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingResources;

/** Both TCP and UDP rules have been confirmed. */
public record ForwardingBecameReady(FloatingIpIdentity identity, String claimId, ForwardingResources resources) implements FloatingIpEvent {}
