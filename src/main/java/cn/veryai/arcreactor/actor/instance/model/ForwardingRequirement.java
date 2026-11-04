package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.util.List;

/** Each service tries the ordered floating IP candidates and targets the instance's prepared parent port. */
public record ForwardingRequirement(List<String> floatingIpIds, List<Integer> internalPorts) implements Serializable {
  public ForwardingRequirement {
    floatingIpIds = List.copyOf(floatingIpIds);
    if (floatingIpIds.isEmpty() || floatingIpIds.stream().anyMatch(String::isBlank)
        || floatingIpIds.stream().distinct().count() != floatingIpIds.size()) {
      throw new IllegalArgumentException("ordered, distinct floating IP IDs are required");
    }
    internalPorts = List.copyOf(internalPorts);
    if (internalPorts.isEmpty() || internalPorts.stream().anyMatch(port -> port < 1 || port > 65535)
        || internalPorts.stream().distinct().count() != internalPorts.size()) {
      throw new IllegalArgumentException("ordered, distinct service ports between 1 and 65535 are required");
    }
  }
}
