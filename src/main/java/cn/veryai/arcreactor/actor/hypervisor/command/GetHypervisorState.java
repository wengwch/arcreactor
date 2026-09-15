package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.model.HypervisorState;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.Objects;

public record GetHypervisorState(ActorRef<HypervisorState> replyTo) implements HypervisorCommand {
    public GetHypervisorState { Objects.requireNonNull(replyTo); }
}
