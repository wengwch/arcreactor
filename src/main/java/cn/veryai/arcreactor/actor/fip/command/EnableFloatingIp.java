package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import cn.veryai.arcreactor.actor.fip.model.PortForwardingSnapshot;
import java.util.List;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record EnableFloatingIp(FloatingIpIdentity identity, byte[] portMask,
    List<PortForwardingSnapshot> forwardings, ActorRef<Boolean> replyTo) implements FloatingIpCommand {
  public EnableFloatingIp {
    Objects.requireNonNull(identity, "identity");
    if (portMask != null && forwardings != null) {
      throw new IllegalArgumentException("provide either portMask or forwardings");
    }
    portMask = portMask == null ? null : portMask.clone();
    forwardings = forwardings == null ? null : List.copyOf(forwardings);
    Objects.requireNonNull(replyTo, "replyTo");
  }

  @Override public byte[] portMask() { return portMask == null ? null : portMask.clone(); }
}
