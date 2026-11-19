package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import org.apache.pekko.actor.typed.ActorRef;

/** Deletes a mapping by claim ID; the instance workflow enforces user-facing deletion restrictions. */
public record DeletePortForwarding(String claimId, ActorRef<ForwardingReply> replyTo) implements FloatingIpCommand {}
