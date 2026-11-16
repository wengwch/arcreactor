package cn.veryai.arcreactor.actor.instance.command;

public record SshConnectivityChecked(long generation, String requestToken, boolean connected,
    String failure) implements WorkflowCommand {}
