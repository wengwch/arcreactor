package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

public record PlacementConstraints(String availabilityZone, Set<String> requiredTraits) implements Serializable {
    public static final PlacementConstraints NONE = new PlacementConstraints(null, Set.of());
    public PlacementConstraints {
        requiredTraits = Set.copyOf(Objects.requireNonNullElse(requiredTraits, Set.of()));
    }
}
