package cn.veryai.arcreactor.actor.hypervisor.event;

import cn.veryai.arcreactor.actor.hypervisor.model.ResourceCapacity;
import java.time.Instant;
import java.util.Objects;

public record CapacityUpdated(ResourceCapacity capacity, Instant updatedAt) implements HypervisorEvent {
    public CapacityUpdated { Objects.requireNonNull(capacity); Objects.requireNonNull(updatedAt); }
}
