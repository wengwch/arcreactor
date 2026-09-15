package cn.veryai.arcreactor.actor.hypervisor.event;

import java.time.Instant;
import java.util.Objects;

public record HypervisorDrainStarted(Instant startedAt) implements HypervisorEvent {
    public HypervisorDrainStarted { Objects.requireNonNull(startedAt); }
}
