package cn.veryai.arcreactor.actor.user.model;

/** Shared naming rules for Manila requests, projections and instance mount credentials. */
public record UserShareNaming(String userId) {
  public UserShareNaming {
    if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
  }

  public String name() {
    return "veryai:sys:" + userId;
  }

  public String description() {
    return name();
  }

  public String accessTo() {
    return "veryai_" + userId;
  }
}
