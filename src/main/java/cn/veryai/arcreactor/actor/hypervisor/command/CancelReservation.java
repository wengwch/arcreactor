package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.reply.ActionReply;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.Objects;

public record CancelReservation(String reservationId, ActorRef<ActionReply> replyTo) implements HypervisorCommand {
    public CancelReservation { Objects.requireNonNull(reservationId); Objects.requireNonNull(replyTo); }
}
