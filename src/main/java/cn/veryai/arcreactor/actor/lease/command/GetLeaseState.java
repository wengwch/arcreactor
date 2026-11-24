package cn.veryai.arcreactor.actor.lease.command;

import cn.veryai.arcreactor.actor.lease.model.LeaseState;
import org.apache.pekko.actor.typed.ActorRef;

public record GetLeaseState(ActorRef<LeaseState> replyTo) implements LeaseCommand {}
