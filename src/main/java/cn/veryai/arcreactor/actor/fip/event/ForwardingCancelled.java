package cn.veryai.arcreactor.actor.fip.event;

/** Tombstones prevent delayed create requests from resurrecting deleted instance mappings. */
public record ForwardingCancelled(String claimId, String instanceId) implements FloatingIpEvent {}
