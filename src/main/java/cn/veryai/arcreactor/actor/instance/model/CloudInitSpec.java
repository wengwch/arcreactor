package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/** Per-instance credentials and static routes used by the cloud-init template. */
public record CloudInitSpec(String username, String password, String jupyterToken,
    Map<String, String> hostRoutes) implements Serializable {
  public CloudInitSpec {
    if (username == null || !username.matches("[a-z_][a-z0-9_-]{0,31}")) {
      throw new IllegalArgumentException("invalid SSH username");
    }
    if (password == null || password.isBlank() || password.indexOf('\n') >= 0
        || password.indexOf('\r') >= 0 || password.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("SSH password is required and must be a single line");
    }
    if (jupyterToken == null || !jupyterToken.matches("[A-Za-z0-9._~-]+")) {
      throw new IllegalArgumentException("Jupyter token must contain only URL-safe characters");
    }
    hostRoutes = Map.copyOf(Objects.requireNonNullElse(hostRoutes, Map.of()));
  }

  @Override
  public String toString() {
    return "CloudInitSpec[username=" + username + ", password=<redacted>, jupyterToken=<redacted>]";
  }
}
