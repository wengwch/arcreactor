package cn.veryai.arcreactor.actor.lease.command;

import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import cn.veryai.arcreactor.actor.lease.reply.LeaseReply;
import org.apache.pekko.actor.typed.ActorRef;

public record CreateLease(String requestId, String fingerprint, InstanceSpec spec,
        RentalPlan plan, boolean autoRenew, ActorRef<LeaseReply> replyTo) implements LeaseCommand {}
