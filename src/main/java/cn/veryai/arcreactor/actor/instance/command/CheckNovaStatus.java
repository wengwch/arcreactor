package cn.veryai.arcreactor.actor.instance.command;

public record CheckNovaStatus(String serverId, long generation, int attempt) implements WorkflowCommand {
    public CheckNovaStatus(String serverId, long generation) {
        this(serverId, generation, 1);
    }
}
