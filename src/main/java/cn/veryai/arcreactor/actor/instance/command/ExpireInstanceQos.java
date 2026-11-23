package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.PortBandwidthLimit;
import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** Expires only the identified QoS record, restoring both ports to the supplied limits. */
public record ExpireInstanceQos(String operationId, PortBandwidthLimit parentPort,
    PortBandwidthLimit subPort, ActorRef<InstanceMutationReply> replyTo) implements WorkflowCommand {
  public ExpireInstanceQos {
    if (operationId == null || operationId.isBlank()) throw new IllegalArgumentException("operationId is required");
    Objects.requireNonNull(parentPort, "parentPort");
    Objects.requireNonNull(subPort, "subPort");
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
