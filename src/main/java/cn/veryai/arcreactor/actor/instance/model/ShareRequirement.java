package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import java.io.Serializable;
import java.util.Objects;

/** The same userId/shareId must be used by every instance sharing this resource. */
public record ShareRequirement(String userId, String shareId, ShareSpec spec) implements Serializable {
  public ShareRequirement {
    if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
    if (shareId == null || shareId.isBlank()) throw new IllegalArgumentException("shareId is required");
    Objects.requireNonNull(spec, "spec");
  }
}
