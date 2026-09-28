package cn.veryai.arcreactor.actor.instance.command;

import cn.veryai.arcreactor.actor.instance.model.InstanceWorkflowState;
import java.util.Objects;
import org.apache.pekko.actor.typed.ActorRef;

public record GetInstanceState(ActorRef<InstanceWorkflowState> replyTo) implements WorkflowCommand {
    public GetInstanceState { Objects.requireNonNull(replyTo, "replyTo"); }
}
