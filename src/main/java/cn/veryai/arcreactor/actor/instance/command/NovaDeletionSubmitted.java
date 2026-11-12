package cn.veryai.arcreactor.actor.instance.command;

/** Result of submitting a Nova delete request; success does not mean the server is absent yet. */
public record NovaDeletionSubmitted(long generation, String requestToken, String osServerId, String failure)
        implements WorkflowCommand {
}
