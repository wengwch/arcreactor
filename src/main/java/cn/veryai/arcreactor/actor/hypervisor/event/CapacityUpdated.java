package cn.veryai.arcreactor.actor.hypervisor.event;

import cn.veryai.arcreactor.actor.hypervisor.model.ResourceCapacity;
import cn.veryai.arcreactor.actor.hypervisor.model.ResourceUsage;
import java.time.Instant;
import java.util.Objects;

public record CapacityUpdated(ResourceCapacity capacity, ResourceUsage baselineUsage, Instant updatedAt)
        implements HypervisorEvent {
    public CapacityUpdated {
        Objects.requireNonNull(capacity);
        Objects.requireNonNull(baselineUsage);
        Objects.requireNonNull(updatedAt);
    }
}
