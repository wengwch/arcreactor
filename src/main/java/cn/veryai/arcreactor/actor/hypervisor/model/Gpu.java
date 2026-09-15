package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

public record Gpu(String id, String model, Set<String> traits) implements Serializable {
    public Gpu {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("gpu id is required");
        model = Objects.requireNonNullElse(model, "unknown");
        traits = Set.copyOf(Objects.requireNonNullElse(traits, Set.of()));
    }
}
