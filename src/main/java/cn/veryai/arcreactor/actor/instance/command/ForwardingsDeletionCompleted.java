package cn.veryai.arcreactor.actor.instance.command;

public record ForwardingsDeletionCompleted(long generation, String requestToken, boolean deleted, String failure)
        implements WorkflowCommand {
}
