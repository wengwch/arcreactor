package cn.veryai.arcreactor.actor.hypervisor.event;

import java.time.Instant;
import java.util.Objects;

public record HypervisorEnabled(Instant enabledAt) implements HypervisorEvent {
    public HypervisorEnabled { Objects.requireNonNull(enabledAt); }
}
