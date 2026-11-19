package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;

/** A rejected or failed destruction attempt returns the address to disabled for retry. */
public record FloatingIpDestructionFailed(FloatingIpIdentity identity) implements FloatingIpEvent {}
