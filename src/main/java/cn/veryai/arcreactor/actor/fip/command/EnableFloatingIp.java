package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record EnableFloatingIp(FloatingIpIdentity identity, byte[] portMask,
    ActorRef<Boolean> replyTo) implements FloatingIpCommand {
  public EnableFloatingIp {
    Objects.requireNonNull(identity, "identity");
    portMask = Objects.requireNonNull(portMask, "portMask").clone();
    Objects.requireNonNull(replyTo, "replyTo");
  }

  @Override public byte[] portMask() { return portMask.clone(); }
}
