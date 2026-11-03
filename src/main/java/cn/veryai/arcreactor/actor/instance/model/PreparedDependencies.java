package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.util.Objects;

/** Resolved user resources, persisted before creating any instance-owned resources. */
public record PreparedDependencies(String projectId, ShareRequirement share, PreparedShare preparedShare,
    NetworkRequirement network) implements Serializable {
  public PreparedDependencies {
    if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("projectId is required");
    Objects.requireNonNull(share, "share");
    Objects.requireNonNull(preparedShare, "preparedShare");
    Objects.requireNonNull(network, "network");
    if (!projectId.equals(share.spec().osProjectId())) {
      throw new IllegalArgumentException("prepared share must belong to the resolved project");
    }
    if (!network.resolved() || network.userNetwork() == null
        || !share.spec().regionId().equals(network.userNetwork().regionId())) {
      throw new IllegalArgumentException("resolved network dependencies must belong to the share region");
    }
  }
}
