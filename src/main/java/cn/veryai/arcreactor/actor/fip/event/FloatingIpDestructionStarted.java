package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;

/** Persists the intent to destroy the address before contacting OpenStack. */
public record FloatingIpDestructionStarted(FloatingIpIdentity identity) implements FloatingIpEvent {}
