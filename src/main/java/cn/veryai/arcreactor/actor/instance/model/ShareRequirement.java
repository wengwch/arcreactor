package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import java.io.Serializable;
import java.util.Objects;

/** Instances with the same userId and spec.regionId share one resource with matching specs. */
public record ShareRequirement(String userId, String shareId, ShareSpec spec) implements Serializable {
  public ShareRequirement {
    if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
    if (shareId == null || shareId.isBlank()) throw new IllegalArgumentException("shareId is required");
    Objects.requireNonNull(spec, "spec");
  }
}
