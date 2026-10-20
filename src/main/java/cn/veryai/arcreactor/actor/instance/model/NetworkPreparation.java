package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.time.Instant;

/** Preset and user network IDs are pinned dependencies; resources contains only instance-owned ports and trunk. */
public record NetworkPreparation(Instant deadline, Step step, boolean createRequested,
    PreparedNetwork resources, String userNetworkId, String userSubnetId,
    String sharedNetworkId, String sharedSubnetId) implements Serializable {
  public enum Step { USER_NETWORK, PARENT_PORT, SUB_PORT, TRUNK, COMPLETE }

  public NetworkPreparation(Instant deadline, Step step, boolean createRequested, PreparedNetwork resources) {
    this(deadline, step, createRequested, resources, null, null, null, null);
  }

  public NetworkPreparation(Instant deadline, Step step, boolean createRequested, PreparedNetwork resources,
      String userNetworkId, String userSubnetId) {
    this(deadline, step, createRequested, resources, userNetworkId, userSubnetId, null, null);
  }

  public NetworkPreparation requested() {
    return new NetworkPreparation(deadline, step, true, resources, userNetworkId, userSubnetId,
        sharedNetworkId, sharedSubnetId);
  }
}
