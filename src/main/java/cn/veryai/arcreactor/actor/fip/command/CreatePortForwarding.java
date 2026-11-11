package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** The destination actor supplies the floating IP and allocates the external TCP/UDP port. */
public record CreatePortForwarding(String claimId, int internalPort, String fixedIp, String osInternalPortId,
    String instanceId, String userId, String regionId, String osProjectId,
    ActorRef<ForwardingReply> replyTo) implements FloatingIpCommand {
  public CreatePortForwarding {
    if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
    new ForwardingSpec(instanceId, userId, regionId, osProjectId, osInternalPortId, fixedIp, internalPort);
    Objects.requireNonNull(replyTo, "replyTo");
  }

  public CreatePortForwarding(String claimId, ForwardingSpec spec, ActorRef<ForwardingReply> replyTo) {
    this(claimId, spec.internalPort(), spec.fixedIp(), spec.osInternalPortId(), spec.instanceId(),
        spec.userId(), spec.regionId(), spec.osProjectId(), replyTo);
  }

  public ForwardingSpec spec() {
    return new ForwardingSpec(instanceId, userId, regionId, osProjectId, osInternalPortId, fixedIp, internalPort);
  }
}
