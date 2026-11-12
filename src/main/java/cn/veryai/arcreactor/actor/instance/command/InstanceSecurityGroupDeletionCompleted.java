package cn.veryai.arcreactor.actor.instance.command;

public record InstanceSecurityGroupDeletionCompleted(long generation, String requestToken, String failure)
        implements WorkflowCommand {
}
