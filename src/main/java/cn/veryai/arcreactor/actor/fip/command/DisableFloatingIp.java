package cn.veryai.arcreactor.actor.fip.command;

import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record DisableFloatingIp(ActorRef<Boolean> replyTo) implements FloatingIpCommand {
  public DisableFloatingIp { Objects.requireNonNull(replyTo, "replyTo"); }
}
