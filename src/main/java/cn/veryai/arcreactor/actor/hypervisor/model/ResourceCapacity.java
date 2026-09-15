package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public record ResourceCapacity(
        int totalVcpus,
        long totalMemoryMb,
        List<Gpu> gpus,
        String availabilityZone) implements Serializable {

    public ResourceCapacity {
        if (totalVcpus < 0) throw new IllegalArgumentException("totalVcpus must be non-negative");
        if (totalMemoryMb < 0) throw new IllegalArgumentException("totalMemoryMb must be non-negative");
        gpus = List.copyOf(Objects.requireNonNullElse(gpus, List.of()));
        if (gpus.stream().map(Gpu::id).distinct().count() != gpus.size()) {
            throw new IllegalArgumentException("GPU ids must be unique");
        }
        availabilityZone = Objects.requireNonNullElse(availabilityZone, "default");
    }

    public ResourceCapacity(int totalVcpus, long totalMemoryMb, List<Gpu> gpus) {
        this(totalVcpus, totalMemoryMb, gpus, "default");
    }

    public static ResourceCapacity empty() {
        return new ResourceCapacity(0, 0, List.of());
    }
}
