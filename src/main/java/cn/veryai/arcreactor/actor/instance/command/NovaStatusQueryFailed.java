package cn.veryai.arcreactor.actor.instance.command;

public record NovaStatusQueryFailed(String serverId, long generation, int attempt,
        String reason) implements WorkflowCommand {
    public NovaStatusQueryFailed(String serverId, long generation, String reason) {
        this(serverId, generation, 1, reason);
    }
}
