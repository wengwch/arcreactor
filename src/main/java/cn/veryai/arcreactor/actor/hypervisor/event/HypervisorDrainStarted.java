package cn.veryai.arcreactor.actor.hypervisor.event;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

public record HypervisorDrainStarted(Instant startedAt, LocalDateTime expectedMaintenanceTime)
        implements HypervisorEvent {
    public HypervisorDrainStarted { Objects.requireNonNull(startedAt); }

    public HypervisorDrainStarted(Instant startedAt) {
        this(startedAt, null);
    }
}
