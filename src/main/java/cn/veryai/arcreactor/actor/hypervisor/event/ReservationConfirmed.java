package cn.veryai.arcreactor.actor.hypervisor.event;

import cn.veryai.arcreactor.actor.hypervisor.model.Reservation;
import java.time.Instant;
import java.util.Objects;

public record ReservationConfirmed(Reservation reservation, Instant allocatedAt) implements HypervisorEvent {
    public ReservationConfirmed { Objects.requireNonNull(reservation); Objects.requireNonNull(allocatedAt); }
}
