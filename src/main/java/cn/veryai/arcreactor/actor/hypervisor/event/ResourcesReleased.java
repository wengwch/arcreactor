package cn.veryai.arcreactor.actor.hypervisor.event;

import cn.veryai.arcreactor.actor.hypervisor.model.Allocation;
import java.time.Instant;
import java.util.Objects;

public record ResourcesReleased(Allocation allocation, Instant releasedAt) implements HypervisorEvent {
    public ResourcesReleased { Objects.requireNonNull(allocation); Objects.requireNonNull(releasedAt); }
}
