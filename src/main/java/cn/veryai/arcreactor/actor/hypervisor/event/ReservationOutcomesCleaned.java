package cn.veryai.arcreactor.actor.hypervisor.event;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record ReservationOutcomesCleaned(Set<String> reservationIds, Instant cleanedAt) implements HypervisorEvent {
    public ReservationOutcomesCleaned {
        reservationIds = Set.copyOf(reservationIds);
        Objects.requireNonNull(cleanedAt, "cleanedAt");
    }
}
