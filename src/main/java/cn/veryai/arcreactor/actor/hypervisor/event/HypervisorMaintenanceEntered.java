package cn.veryai.arcreactor.actor.hypervisor.event;

import java.time.Instant;
import java.util.Objects;

public record HypervisorMaintenanceEntered(Instant enteredAt) implements HypervisorEvent {
    public HypervisorMaintenanceEntered { Objects.requireNonNull(enteredAt); }
}
