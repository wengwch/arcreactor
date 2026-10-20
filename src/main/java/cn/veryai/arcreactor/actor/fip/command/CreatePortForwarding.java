package cn.veryai.arcreactor.actor.fip.command;

import cn.veryai.arcreactor.actor.fip.model.ForwardingSpec;
import cn.veryai.arcreactor.actor.fip.reply.ForwardingReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** The destination actor supplies the floating IP and allocates the external TCP/UDP port. */
public record CreatePortForwarding(String claimId, int internalPort, String fixedIp, String internalPortId,
    String instanceId, String userId, String regionId, String projectId, String securityGroupId,
    ActorRef<ForwardingReply> replyTo) implements FloatingIpCommand {
  public CreatePortForwarding {
    if (claimId == null || claimId.isBlank()) throw new IllegalArgumentException("claimId is required");
    new ForwardingSpec(instanceId, userId, regionId, projectId, internalPortId, fixedIp, internalPort, securityGroupId);
    Objects.requireNonNull(replyTo, "replyTo");
  }

  public CreatePortForwarding(String claimId, ForwardingSpec spec, ActorRef<ForwardingReply> replyTo) {
    this(claimId, spec.internalPort(), spec.fixedIp(), spec.internalPortId(), spec.instanceId(),
        spec.userId(), spec.regionId(), spec.projectId(), spec.securityGroupId(), replyTo);
  }

  public ForwardingSpec spec() {
    return new ForwardingSpec(instanceId, userId, regionId, projectId, internalPortId, fixedIp, internalPort, securityGroupId);
  }
}
