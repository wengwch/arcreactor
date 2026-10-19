package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.user.reply.ProjectReply;

public record ProjectStatusCompleted(long generation, int attempt, boolean submission,
    ProjectReply reply) implements WorkflowCommand {}
