package cn.veryai.arcreactor.actor.user.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Shared naming rules for Manila requests, projections and instance mount credentials. */
public final class UserShareNaming {
  private UserShareNaming() {}

  public static String name(String userId, ShareKind kind, String shareId) {
    return kind == ShareKind.SYSTEM
        ? "veryai:sys:" + userShareToken(userId, shareId)
        : "veryai:user:" + userShareToken(userId, shareId);
  }

  public static String description(String userId, ShareKind kind, String shareId) {
    return "veryai:" + userId;
  }

  public static String accessTo(String userId, ShareKind kind, String shareId) {
    return "veryai_" + userId;
  }

  private static String userShareToken(String userId, String shareId) {
    // Keep remote names bounded and distinguish user/ID boundaries without truncating either ID.
    String identity = userId + ":" + shareId;
    return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
  }

}
