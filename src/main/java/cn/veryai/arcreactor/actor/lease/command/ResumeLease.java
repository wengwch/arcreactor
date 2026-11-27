package cn.veryai.arcreactor.actor.lease.command;

import cn.veryai.arcreactor.actor.lease.reply.LeaseReply;
import org.apache.pekko.actor.typed.ActorRef;

public record ResumeLease(ActorRef<LeaseReply> replyTo) implements LeaseCommand {}
