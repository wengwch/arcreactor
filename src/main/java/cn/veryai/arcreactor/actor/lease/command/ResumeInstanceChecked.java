package cn.veryai.arcreactor.actor.lease.command;

import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.lease.reply.LeaseReply;
import org.apache.pekko.actor.typed.ActorRef;

public record ResumeInstanceChecked(String pollId, InstanceWorkflowState instance, String error,
    ActorRef<LeaseReply> replyTo) implements LeaseCommand {}
