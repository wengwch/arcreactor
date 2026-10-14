package cn.veryai.arcreactor.actor.fip.command;

import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** Returns true only after all of this instance's claims have finished cleanup. */
public record DeleteInstancePortForwardings(String instanceId, ActorRef<Boolean> replyTo)
    implements FloatingIpCommand {
  public DeleteInstancePortForwardings {
    if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
    Objects.requireNonNull(replyTo);
  }
}
