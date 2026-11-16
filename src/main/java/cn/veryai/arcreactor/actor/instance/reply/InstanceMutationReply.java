package cn.veryai.arcreactor.actor.instance.reply;

import java.io.Serializable;

/** Acceptance confirms the mutation intent is persisted; query the instance for completion. */
public record InstanceMutationReply(boolean accepted, String operationId, String detail) implements Serializable {
  public static InstanceMutationReply accepted(String operationId) {
    if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operationId is required");
    return new InstanceMutationReply(true, operationId, "instance mutation accepted");
  }

  public static InstanceMutationReply rejected(String detail) {
    return new InstanceMutationReply(false, null, detail);
  }
}
