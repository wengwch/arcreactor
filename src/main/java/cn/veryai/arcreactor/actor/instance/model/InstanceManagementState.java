package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Management operations do not change the running instance's lifecycle phase. */
public record InstanceManagementState(Map<String, UserForwarding> userForwardings,
    InstanceQosUpdate qosUpdate) implements Serializable {
  public InstanceManagementState { userForwardings = Map.copyOf(userForwardings); }
  public static InstanceManagementState empty() { return new InstanceManagementState(Map.of(), null); }
  public boolean busy() {
    return userForwardings.values().stream().anyMatch(UserForwarding::inProgress)
        || qosUpdate != null && qosUpdate.phase() == InstanceQosUpdate.Phase.UPDATING;
  }
  public InstanceManagementState withForwarding(UserForwarding forwarding) {
    Map<String, UserForwarding> next = new LinkedHashMap<>(userForwardings);
    next.put(forwarding.forwardingId(), forwarding);
    return new InstanceManagementState(next, qosUpdate);
  }
  public InstanceManagementState withQos(InstanceQosUpdate update) {
    return new InstanceManagementState(userForwardings, update);
  }
}

