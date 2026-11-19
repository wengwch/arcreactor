package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.FloatingIpIdentity;
import java.util.List;
import org.apache.pekko.actor.typed.ActorRef;

public record EnableFloatingIp(FloatingIpIdentity identity,
    List<Integer> externalPorts, ActorRef<Boolean> replyTo) implements FloatingIpCommand {
  public EnableFloatingIp {
    externalPorts = List.copyOf(externalPorts);
  }
}
