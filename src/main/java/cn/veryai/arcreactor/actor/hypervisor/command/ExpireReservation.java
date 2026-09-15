package cn.veryai.arcreactor.actor.hypervisor.command;

import java.time.Instant;
import java.util.Objects;

public record ExpireReservation(String reservationId, Instant expectedExpiresAt) implements HypervisorCommand {
    public ExpireReservation { Objects.requireNonNull(reservationId); Objects.requireNonNull(expectedExpiresAt); }
}
