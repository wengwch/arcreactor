package cn.veryai.arcreactor.actor.user.event;

import cn.veryai.arcreactor.actor.user.model.NetworkResources;

/** Confirms that the network, subnet, router and router interface have been created. */
public record NetworkBecameReady(String regionId, NetworkResources resources) implements UserEvent {}
