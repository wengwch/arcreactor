package cn.veryai.arcreactor.actor.hypervisor.event;

import cn.veryai.arcreactor.actor.hypervisor.model.Reservation;
import java.time.Instant;
import java.util.Objects;

public record ReservationExpired(Reservation reservation, Instant expiredAt) implements HypervisorEvent {
    public ReservationExpired { Objects.requireNonNull(reservation); Objects.requireNonNull(expiredAt); }
}
