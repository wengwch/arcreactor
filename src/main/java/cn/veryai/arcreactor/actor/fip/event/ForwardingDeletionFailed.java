package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;

/** Deletion was not confirmed; the port remains reserved. */
public record ForwardingDeletionFailed(FloatingIpIdentity identity, String claimId, String failure) implements FloatingIpEvent {}
