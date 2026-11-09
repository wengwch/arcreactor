package cn.veryai.arcreactor.actor.instance.command;

public record DeletionCompleted(long generation, String requestToken, boolean success, String reason)
        implements WorkflowCommand {
}
