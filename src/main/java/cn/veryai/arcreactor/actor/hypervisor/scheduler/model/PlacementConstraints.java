package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import java.io.Serializable;

public record PlacementConstraints(String availabilityZone) implements Serializable {
    public static final PlacementConstraints NONE = new PlacementConstraints(null);
}
