package cn.veryai.arcreactor.actor.user.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/** Projects are indexed by OpenStack clusterId; shares and networks are indexed by regionId. */
public record UserState(Map<String, UserShareState> shares,
    Map<String, UserProjectState> projects,
    Map<String, UserNetworkState> networks) implements Serializable {
  public UserState {
    shares = Map.copyOf(shares);
    projects = Map.copyOf(projects);
    networks = Map.copyOf(networks);
  }

  public UserState updated(UserShareState share) {
    UserShareState existing = shares.get(share.regionId());
    if (existing != null && !existing.shareId().equals(share.shareId())) {
      throw new IllegalStateException("Multiple shares exist in region " + share.regionId());
    }
    Map<String, UserShareState> next = new HashMap<>(shares);
    next.put(share.regionId(), share);
    return new UserState(next, projects, networks);
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
