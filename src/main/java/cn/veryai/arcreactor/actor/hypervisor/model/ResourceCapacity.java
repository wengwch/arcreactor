package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;
import java.util.Objects;

/** Hypervisor capacity; memory is measured in GB. */
public record ResourceCapacity(
        int totalVcpus,
        long totalMemoryGb,
        int gpus,
        String availabilityZone) implements Serializable {

    public ResourceCapacity {
        if (totalVcpus < 0) throw new IllegalArgumentException("totalVcpus must be non-negative");
        if (totalMemoryGb < 0) throw new IllegalArgumentException("totalMemoryGb must be non-negative");
        if (gpus < 0) throw new IllegalArgumentException("gpus must be non-negative");
        availabilityZone = Objects.requireNonNullElse(availabilityZone, "default");
    }

    public ResourceCapacity(int totalVcpus, long totalMemoryGb, int gpus) {
        this(totalVcpus, totalMemoryGb, gpus, "default");
    }

    public static ResourceCapacity empty() {
        return new ResourceCapacity(0, 0, 0);
    }
}
