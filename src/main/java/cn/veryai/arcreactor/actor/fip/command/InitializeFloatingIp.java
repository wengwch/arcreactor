package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.util.Objects;

public record InitializeFloatingIp(FloatingIpIdentity identity) implements FloatingIpCommand {
  public InitializeFloatingIp { Objects.requireNonNull(identity, "identity"); }
}
