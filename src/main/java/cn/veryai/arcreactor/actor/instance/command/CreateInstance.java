package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.InstanceSpec;
import cn.veryai.arcreactor.actor.instance.reply.InstanceReply;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record CreateInstance(String requestId, String instanceId, InstanceSpec spec,
        ActorRef<InstanceReply> replyTo) implements WorkflowCommand {
    public CreateInstance {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(replyTo, "replyTo");
    }
}
