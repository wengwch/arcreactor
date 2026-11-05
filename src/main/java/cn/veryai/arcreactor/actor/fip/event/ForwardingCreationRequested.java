package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import java.time.Instant;

/** The creation intent and its deadline are persisted before contacting OpenStack. */
public record ForwardingCreationRequested(FloatingIpIdentity identity, String claimId, ForwardingSpec spec,
    Instant deadline) implements FloatingIpEvent {}
