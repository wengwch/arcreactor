package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import cn.veryai.arcreactor.actor.instance.model.PreparedForwarding;
import cn.veryai.arcreactor.util.SSHValidator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/** A single SSH probe through the persisted TCP forwarding; scheduling belongs to the workflow. */
final class InstanceSshChecker {
  private static final int CONNECTION_TIMEOUT_MILLIS = 5_000;
  private static final Executor SSH_EXECUTOR =
      task -> Thread.ofVirtual().name("instance-ssh-check").start(task);

  private final SSHValidator validator;

  InstanceSshChecker(SSHValidator validator) {
    this.validator = Objects.requireNonNull(validator);
  }

  CompletionStage<Boolean> check(InstanceWorkflowState state) {
    PreparedForwarding forwarding = state.preparedForwardings().get(22);
    if (forwarding == null) throw new IllegalStateException("Missing prepared SSH forwarding for internal port 22");
    if (forwarding.floatingIp() == null || forwarding.floatingIp().isBlank()
        || forwarding.externalPort() < 1 || forwarding.externalPort() > 65535
        || forwarding.osTcpForwardingId() == null || forwarding.osTcpForwardingId().isBlank()) {
      throw new IllegalStateException("SSH forwarding is missing its floating IP, external port or TCP rule ID");
    }
    String username = state.spec().cloudInit().username();
    return CompletableFuture.supplyAsync(() -> validator.validateSSH(
        forwarding.floatingIp(), forwarding.externalPort(), username, CONNECTION_TIMEOUT_MILLIS), SSH_EXECUTOR);
  }
}
