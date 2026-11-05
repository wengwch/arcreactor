package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.time.Instant;

/** An explicit request resumes a paused operation with a new deadline. */
public record ForwardingReconciliationRequested(FloatingIpIdentity identity, String claimId, Instant deadline) implements FloatingIpEvent {}
