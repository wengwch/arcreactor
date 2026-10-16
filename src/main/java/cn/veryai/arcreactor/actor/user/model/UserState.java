package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Both resource maps are indexed by regionId within this user. */
public record UserState(Map<String, UserShareState> shares,
    Map<String, UserProjectState> projects) implements Serializable {
  public UserState {
    // Reindex snapshots written when shares were keyed by logical shareId.
    Map<String, UserShareState> sharesByRegion = new HashMap<>();
    for (UserShareState share : shares.values()) {
      UserShareState existing = sharesByRegion.putIfAbsent(share.regionId(), share);
      if (existing != null && !existing.equals(share)) {
        throw new IllegalStateException("Multiple shares exist in region " + share.regionId());
      }
    }
    shares = Map.copyOf(sharesByRegion);
    // Snapshots written before project support have no projects field.
    projects = Map.copyOf(Objects.requireNonNullElse(projects, Map.of()));
  }

  public UserState(Map<String, UserShareState> shares) { this(shares, Map.of()); }

  public UserState updated(UserShareState share) {
    UserShareState existing = shares.get(share.regionId());
    if (existing != null && !existing.shareId().equals(share.shareId())) {
      throw new IllegalStateException("Multiple shares exist in region " + share.regionId());
    }
    Map<String, UserShareState> next = new HashMap<>(shares);
    next.put(share.regionId(), share);
    return new UserState(next, projects);
  }

  public UserState updated(UserProjectState project) {
    Map<String, UserProjectState> next = new HashMap<>(projects);
    next.put(project.regionId(), project);
    return new UserState(shares, next);
  }
}
