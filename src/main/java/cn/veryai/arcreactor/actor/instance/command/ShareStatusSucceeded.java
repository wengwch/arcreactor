package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.user.reply.ShareReply;

public record ShareStatusSucceeded(long generation, int attempt, boolean submission,
    ShareReply reply) implements WorkflowCommand {}
