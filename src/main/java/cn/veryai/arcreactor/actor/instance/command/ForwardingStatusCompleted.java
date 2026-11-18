package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;

/** A null reply with no failure code schedules another query for this claim. */
public record ForwardingStatusCompleted(String claimId, long generation, String requestToken, int attempt, boolean submission,
    ForwardingReply reply, String failureCode, String failure) implements WorkflowCommand {}
