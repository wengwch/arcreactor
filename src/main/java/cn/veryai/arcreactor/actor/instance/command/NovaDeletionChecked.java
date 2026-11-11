package cn.veryai.arcreactor.actor.instance.command;

/** A null server ID without a failure means Nova confirmed the server is absent. */
public record NovaDeletionChecked(long generation, String requestToken, String osServerId, String failure)
        implements WorkflowCommand {
}
