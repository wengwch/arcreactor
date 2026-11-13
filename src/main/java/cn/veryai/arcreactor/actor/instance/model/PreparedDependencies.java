package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.util.Objects;

/** Resolved user resources, persisted before creating any instance-owned resources. */
public record PreparedDependencies(String osProjectId, ShareRequirement share, PreparedShare preparedShare,
    NetworkRequirement network) implements Serializable {
  public PreparedDependencies {
    if (osProjectId == null || osProjectId.isBlank()) throw new IllegalArgumentException("osProjectId is required");
    Objects.requireNonNull(share, "share");
    Objects.requireNonNull(preparedShare, "preparedShare");
    Objects.requireNonNull(network, "network");
    if (share.shareId() == null || share.shareId().isBlank()) {
      throw new IllegalArgumentException("prepared shareId is required");
    }
    if (!osProjectId.equals(share.spec().osProjectId())) {
      throw new IllegalArgumentException("prepared share must belong to the resolved project");
    }
    if (!network.resolved() || network.userNetwork() == null
        || !share.spec().regionId().equals(network.userNetwork().regionId())) {
      throw new IllegalArgumentException("resolved network dependencies must belong to the share region");
    }
  }
}
