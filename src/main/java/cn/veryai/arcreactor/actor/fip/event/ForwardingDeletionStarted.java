package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.time.Instant;

/** Starts deletion of both protocol rules, including compensation after a creation failure. */
public record ForwardingDeletionStarted(FloatingIpIdentity identity, String claimId, Instant deadline) implements FloatingIpEvent {}
