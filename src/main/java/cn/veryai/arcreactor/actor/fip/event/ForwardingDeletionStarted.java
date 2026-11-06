package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.time.Instant;

/** Starts deletion of both protocol rules in response to an external delete command. */
public record ForwardingDeletionStarted(FloatingIpIdentity identity, String claimId, Instant deadline) implements FloatingIpEvent {}
