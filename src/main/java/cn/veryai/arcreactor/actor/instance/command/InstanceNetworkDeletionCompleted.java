package cn.veryai.arcreactor.actor.instance.command;

public record InstanceNetworkDeletionCompleted(long generation, String requestToken, String failure)
        implements WorkflowCommand {
}
