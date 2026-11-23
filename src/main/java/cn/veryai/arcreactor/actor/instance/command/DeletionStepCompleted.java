package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.InstancePhase;

/** Only confirmed success advances a deletion step; unknown or missing results remain retryable. */
public record DeletionStepCompleted(InstancePhase phase, long generation, String requestToken,
    boolean succeeded, String failure) implements WorkflowCommand {
  public DeletionStepCompleted {
    if (phase != InstancePhase.DELETING_NETWORK && phase != InstancePhase.DELETING_SECURITY_GROUP
        && phase != InstancePhase.CANCELLING_RESERVATION && phase != InstancePhase.RELEASING_ALLOCATION) {
      throw new IllegalArgumentException("Unsupported deletion step: " + phase);
    }
  }
}
