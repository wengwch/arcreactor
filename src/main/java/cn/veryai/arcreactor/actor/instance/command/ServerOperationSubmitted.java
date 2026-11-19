package cn.veryai.arcreactor.actor.instance.command;

/** Local result of the single reboot or rebuild submission. */
public record ServerOperationSubmitted(String operationId, String requestToken,
    boolean rejected, String failure) implements WorkflowCommand {}
