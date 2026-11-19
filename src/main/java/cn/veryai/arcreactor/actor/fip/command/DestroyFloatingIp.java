package cn.veryai.arcreactor.actor.fip.command;

import org.apache.pekko.actor.typed.ActorRef;

/** Acknowledges acceptance after persisting destruction intent; query state for completion. */
public record DestroyFloatingIp(ActorRef<Boolean> replyTo) implements FloatingIpCommand {}
