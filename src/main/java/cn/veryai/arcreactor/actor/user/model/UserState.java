package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** Shares are indexed by shareId, projects by clusterId and networks by regionId. */
public record UserState(Map<String, UserShareState> shares,
    Map<String, UserProjectState> projects,
    Map<String, UserNetworkState> networks) implements Serializable {
  public UserState {
    // Reindex older snapshots whose shares were keyed by regionId.
    Map<String, UserShareState> indexedShares = new HashMap<>();
    Map<String, String> systemRegions = new HashMap<>();
    for (UserShareState share : shares.values()) {
      UserShareState previous = indexedShares.putIfAbsent(share.shareId(), share);
      if (previous != null && !previous.equals(share)) {
        throw new IllegalStateException("Conflicting share ID " + share.shareId());
      }
      if (share.spec().kind() == ShareKind.SYSTEM) {
        String existingId = systemRegions.putIfAbsent(share.regionId(), share.shareId());
        if (existingId != null && !existingId.equals(share.shareId())) {
          throw new IllegalStateException("Multiple system shares exist in region " + share.regionId());
        }
      }
    }
    shares = Map.copyOf(indexedShares);
    projects = Map.copyOf(projects);
    networks = Map.copyOf(networks);
  }

  public UserState updated(UserShareState share) {
    UserShareState existing = shares.get(share.shareId());
    if (existing != null && !existing.spec().equals(share.spec())) {
      throw new IllegalStateException("Share specification cannot change: " + share.shareId());
    }
    Map<String, UserShareState> next = new HashMap<>(shares);
    next.put(share.shareId(), share);
    return new UserState(next, projects, networks);
  }

  public UserShareState systemShare(String regionId) {
    return shares.values().stream()
        .filter(share -> share.spec().kind() == ShareKind.SYSTEM && share.regionId().equals(regionId))
        .findFirst().orElse(null);
  }

  public UserShareState userShare(String regionId) {
    return shares.values().stream()
        .filter(share -> share.spec().kind() == ShareKind.USER && share.regionId().equals(regionId))
        .findFirst().orElse(null);
  }

  public UserState updated(UserProjectState project) {
    Map<String, UserProjectState> next = new HashMap<>(projects);
    next.put(project.clusterId(), project);
    return new UserState(shares, next, networks);
  }

  public UserState updated(UserNetworkState network) {
    Map<String, UserNetworkState> next = new HashMap<>(networks);
    next.put(network.regionId(), network);
    return new UserState(shares, projects, next);
  }
}
