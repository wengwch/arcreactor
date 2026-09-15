package cn.veryai.arcreactor.actor.hypervisor.event;

import cn.veryai.arcreactor.actor.hypervisor.model.Reservation;
import java.time.Instant;
import java.util.Objects;

public record ReservationCancelled(Reservation reservation, Instant cancelledAt) implements HypervisorEvent {
    public ReservationCancelled { Objects.requireNonNull(reservation); Objects.requireNonNull(cancelledAt); }
}
