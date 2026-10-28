package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;

/** A null reply represents an uncertain ask outcome and must be followed by a query. */
public record ForwardingStatusCompleted(long generation, String requestToken, int attempt, boolean submission,
    ForwardingReply reply) implements WorkflowCommand {}
