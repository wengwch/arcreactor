package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record InitializeFloatingIp(FloatingIpIdentity identity, byte[] portMask,
    ActorRef<Boolean> replyTo) implements FloatingIpCommand {
  public InitializeFloatingIp {
    Objects.requireNonNull(identity, "identity");
    portMask = portMask == null ? null : portMask.clone();
  }

  /** Identity-only messages reactivate an actor without changing its initial port reservations. */
  public InitializeFloatingIp(FloatingIpIdentity identity) {
    this(identity, null, null);
  }

  @Override public byte[] portMask() { return portMask == null ? null : portMask.clone(); }
}
