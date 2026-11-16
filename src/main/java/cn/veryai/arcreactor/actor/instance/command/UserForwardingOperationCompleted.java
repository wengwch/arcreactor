package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import cn.veryai.arcreactor.actor.instance.model.UserForwarding;

public record UserForwardingOperationCompleted(String forwardingId, long generation, UserForwarding.Phase phase,
    String requestToken, ForwardingReply reply, boolean rejected, String failure) implements WorkflowCommand {}

