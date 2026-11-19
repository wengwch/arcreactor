package cn.veryai.arcreactor.actor.fip.event;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;

public record FloatingIpDestroyed(FloatingIpIdentity identity) implements FloatingIpEvent {}
