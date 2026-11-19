package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Durable intent and outcome for an operation on the existing Nova server. */
public record InstanceServerOperation(String operationId, Type type, Phase phase,
    String osImageId, Instant requestedAt, String failure) implements Serializable {
  public enum Type {
    REBOOT, REBUILD;

    public InstancePhase instancePhase() {
      return this == REBOOT ? InstancePhase.REBOOTING : InstancePhase.REBUILDING;
    }
  }

  public enum Phase { SUBMITTING, RUNNING, SUCCEEDED, FAILED }

  public InstanceServerOperation {
    if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operationId is required");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(phase, "phase");
    Objects.requireNonNull(requestedAt, "requestedAt");
    if (type == Type.REBUILD && (osImageId == null || osImageId.isBlank())) {
      throw new IllegalArgumentException("osImageId is required for rebuilding");
    }
  }

  public boolean inProgress() {
    return phase == Phase.SUBMITTING || phase == Phase.RUNNING;
  }

  public InstanceServerOperation running() {
    return new InstanceServerOperation(operationId, type, Phase.RUNNING, osImageId, requestedAt, null);
  }

  public InstanceServerOperation succeeded() {
    return new InstanceServerOperation(operationId, type, Phase.SUCCEEDED, osImageId, requestedAt, null);
  }

  public InstanceServerOperation failed(String detail) {
    return new InstanceServerOperation(operationId, type, Phase.FAILED, osImageId, requestedAt, detail);
  }
}
