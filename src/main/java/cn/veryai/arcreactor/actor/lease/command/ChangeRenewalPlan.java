package cn.veryai.arcreactor.actor.lease.command;

import cn.veryai.arcreactor.actor.lease.model.RentalPlan;
import cn.veryai.arcreactor.actor.lease.reply.LeaseReply;
import org.apache.pekko.actor.typed.ActorRef;

public record ChangeRenewalPlan(long targetPeriodSeq, long expectedPlanRevision, RentalPlan plan,
        ActorRef<LeaseReply> replyTo) implements LeaseCommand {}
