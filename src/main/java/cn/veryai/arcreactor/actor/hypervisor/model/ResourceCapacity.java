package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;
import java.util.Objects;

public record ResourceCapacity(
        int totalVcpus,
        long totalMemoryMb,
        int gpus,
        String availabilityZone) implements Serializable {

    public ResourceCapacity {
        if (totalVcpus < 0) throw new IllegalArgumentException("totalVcpus must be non-negative");
        if (totalMemoryMb < 0) throw new IllegalArgumentException("totalMemoryMb must be non-negative");
        if (gpus < 0) throw new IllegalArgumentException("gpus must be non-negative");
        availabilityZone = Objects.requireNonNullElse(availabilityZone, "default");
    }

    public ResourceCapacity(int totalVcpus, long totalMemoryMb, int gpus) {
        this(totalVcpus, totalMemoryMb, gpus, "default");
    }

    public static ResourceCapacity empty() {
        return new ResourceCapacity(0, 0, 0);
    }
}
