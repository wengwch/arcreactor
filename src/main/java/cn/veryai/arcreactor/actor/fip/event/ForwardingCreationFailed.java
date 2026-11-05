package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;

/** Creation failed; uncertain outcomes retain their port until reconciliation. */
public record ForwardingCreationFailed(FloatingIpIdentity identity, String claimId, String failure, boolean uncertain) implements FloatingIpEvent {}
