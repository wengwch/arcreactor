package cn.veryai.arcreactor.actor.instance.command;

/** A null server ID without a failure means Nova confirmed the server is absent. */
public record NovaDeletionChecked(long generation, String requestToken, String serverId, String failure)
        implements WorkflowCommand {
}
