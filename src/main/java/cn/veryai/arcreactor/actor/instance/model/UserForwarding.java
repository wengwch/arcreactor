package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.fip.model.ForwardingKind;
import java.io.Serializable;

/** A user-owned mapping; its identity remains available even if creation fails before returning resources. */
public record UserForwarding(String forwardingId, String floatingIpId, String osFloatingIpId, int internalPort,
    Phase phase, PreparedForwarding resources, String failure, long generation) implements Serializable {
  public enum Phase { CREATING, READY, FAILED, DELETING, DELETE_FAILED, DELETED }

  public boolean inProgress() { return phase == Phase.CREATING || phase == Phase.DELETING; }
  public UserForwarding ready(PreparedForwarding prepared) {
    return new UserForwarding(forwardingId, floatingIpId, osFloatingIpId, internalPort, Phase.READY, prepared, null, generation + 1);
  }
  public UserForwarding transition(Phase next, String detail) {
    return new UserForwarding(forwardingId, floatingIpId, osFloatingIpId, internalPort, next, resources, detail, generation + 1);
  }
  public PreparedForwarding deletionTarget() {
    return resources != null ? resources : new PreparedForwarding(forwardingId, floatingIpId, osFloatingIpId, null, null,
        internalPort, 0, null, null, ForwardingKind.USER);
  }
}

