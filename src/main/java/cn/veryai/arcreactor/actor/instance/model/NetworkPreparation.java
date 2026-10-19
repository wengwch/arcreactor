package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.time.Instant;

public record NetworkPreparation(Instant deadline, Step step, boolean createRequested,
    PreparedNetwork resources) implements Serializable {
  public enum Step { PARENT_PORT, SUB_PORT, TRUNK, COMPLETE }

  public NetworkPreparation requested() {
    return new NetworkPreparation(deadline, step, true, resources);
  }

  public NetworkPreparation prepared(PreparedNetwork prepared) {
    Step next = switch (step) {
      case PARENT_PORT -> Step.SUB_PORT;
      case SUB_PORT -> Step.TRUNK;
      case TRUNK, COMPLETE -> Step.COMPLETE;
    };
    return new NetworkPreparation(deadline, next, false, prepared);
  }
}
