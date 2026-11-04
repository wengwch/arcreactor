package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record EnableFloatingIp(FloatingIpIdentity identity, byte[] portMask,
    ActorRef<Boolean> replyTo) implements FloatingIpCommand {
  public EnableFloatingIp {
    Objects.requireNonNull(identity, "identity");
    portMask = portMask == null ? null : portMask.clone();
  }

  /** Identity-only enable messages reactivate an actor without changing its initial port reservations. */
  public EnableFloatingIp(FloatingIpIdentity identity) {
    this(identity, null, null);
  }

  @Override public byte[] portMask() { return portMask == null ? null : portMask.clone(); }
}
