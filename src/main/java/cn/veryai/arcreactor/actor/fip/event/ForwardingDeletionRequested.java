package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;

/** Cancellation intent is recorded without discarding an in-flight creation result. */
public record ForwardingDeletionRequested(FloatingIpIdentity identity, String claimId) implements FloatingIpEvent {}
