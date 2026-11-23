package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.PortBandwidthLimit;
import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.time.Instant;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record UpdateInstanceQos(PortBandwidthLimit parentPort, PortBandwidthLimit subPort,
    Instant expiresAt, ActorRef<InstanceMutationReply> replyTo) implements WorkflowCommand {
  public UpdateInstanceQos {
    if (parentPort == null && subPort == null) throw new IllegalArgumentException("at least one port limit is required");
    Objects.requireNonNull(expiresAt, "expiresAt");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
