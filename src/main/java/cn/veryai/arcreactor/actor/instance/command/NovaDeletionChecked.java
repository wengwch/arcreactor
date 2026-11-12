package cn.veryai.arcreactor.actor.instance.command;

/** Read-only lookup result; a null server ID without a failure means the server is absent. */
public record NovaDeletionChecked(long generation, String requestToken, String osServerId, String failure)
        implements WorkflowCommand {
}
