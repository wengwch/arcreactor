package cn.veryai.arcreactor.actor.instance.model;

import cn.veryai.arcreactor.actor.user.model.ShareSpec;
import cn.veryai.arcreactor.actor.user.model.ShareKind;
import java.io.Serializable;
import java.util.Objects;

/** Instances reuse the user's single system share in the region; shareId is null until resolved. */
public record ShareRequirement(String userId, String shareId, ShareSpec spec) implements Serializable {
  public ShareRequirement {
    if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
    if (shareId != null && shareId.isBlank()) throw new IllegalArgumentException("shareId must not be blank");
    Objects.requireNonNull(spec, "spec");
    if (spec.kind() != ShareKind.SYSTEM) throw new IllegalArgumentException("Instances require a system share");
  }

  public ShareRequirement(String userId, ShareSpec spec) {
    this(userId, null, spec);
  }
}
