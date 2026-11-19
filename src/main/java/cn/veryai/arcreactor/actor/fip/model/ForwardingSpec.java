package cn.veryai.arcreactor.actor.fip.model;

import java.io.Serializable;

/** The internal service endpoint; the destination actor identifies the floating IP. */
public record ForwardingSpec(String instanceId, String userId, String regionId, String osProjectId,
    String osInternalPortId, String fixedIp, int internalPort, ForwardingKind kind) implements Serializable {}
