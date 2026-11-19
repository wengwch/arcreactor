package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import org.apache.pekko.actor.typed.ActorRef;

public record GetPortForwarding(String claimId, ActorRef<ForwardingReply> replyTo) implements FloatingIpCommand {}
