package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.reply.InstanceMutationReply;
import java.util.List;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

/** Creates a user forwarding. Empty candidates use the instance's default selection. */
public record CreateInstanceForwarding(int internalPort, List<String> floatingIpIds,
    ActorRef<InstanceMutationReply> replyTo) implements WorkflowCommand {
  public CreateInstanceForwarding {
    if (internalPort < 1 || internalPort > 65535) throw new IllegalArgumentException("invalid internal port");
    floatingIpIds = floatingIpIds == null ? List.of() : List.copyOf(floatingIpIds);
    if (floatingIpIds.stream().anyMatch(String::isBlank)
        || floatingIpIds.stream().distinct().count() != floatingIpIds.size()) {
      throw new IllegalArgumentException("ordered, distinct floating IP IDs must not be blank");
    }
    Objects.requireNonNull(replyTo, "replyTo");
  }
}
