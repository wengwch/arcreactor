package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.ForwardingClaim;

public record ForwardingUpdated(ForwardingClaim claim) implements FloatingIpEvent {}
