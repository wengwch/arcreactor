package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.user.reply.NetworkReply;

public record UserNetworkStatusCompleted(long generation, boolean submission, NetworkReply reply)
    implements WorkflowCommand {}
