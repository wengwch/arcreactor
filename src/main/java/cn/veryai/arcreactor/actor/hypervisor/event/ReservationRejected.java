package cn.veryai.arcreactor.actor.hypervisor.event;

import java.time.Instant;
import java.util.Objects;

/** Records that this host will not allocate resources for the reservation. */
public record ReservationRejected(String reservationId, Instant rejectedAt) implements HypervisorEvent {
    public ReservationRejected {
        if (reservationId == null || reservationId.isBlank()) {
            throw new IllegalArgumentException("reservationId is required");
        }
        Objects.requireNonNull(rejectedAt, "rejectedAt");
    }
}
