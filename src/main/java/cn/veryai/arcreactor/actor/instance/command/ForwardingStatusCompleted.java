package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;

/** A null reply without a failure code polls the same claim; failure preserves the original exception reason. */
public record ForwardingStatusCompleted(String claimId, long generation, String requestToken, int attempt, boolean submission,
    ForwardingReply reply, String failureCode, String failure) implements WorkflowCommand {}
