package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.reply.ConfirmReply;
import org.apache.pekko.actor.typed.ActorRef;

import java.util.Objects;

public record ConfirmReservation(String reservationId, String instanceId, ActorRef<ConfirmReply> replyTo)
        implements HypervisorCommand {
    public ConfirmReservation { Objects.requireNonNull(reservationId); Objects.requireNonNull(instanceId); Objects.requireNonNull(replyTo); }
}
