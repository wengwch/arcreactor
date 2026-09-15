package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

public record GpuRequest(int count, Set<String> requiredTraits) implements Serializable {
    public static final GpuRequest NONE = new GpuRequest(0, Set.of());

    public GpuRequest {
        if (count < 0) throw new IllegalArgumentException("gpu count must be non-negative");
        requiredTraits = Set.copyOf(Objects.requireNonNullElse(requiredTraits, Set.of()));
    }
}
