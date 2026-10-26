package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import java.io.Serializable;

public record PlacementConstraints(String availabilityZone, String regionId, String hypervisorType)
        implements Serializable {
    public static final PlacementConstraints NONE = new PlacementConstraints(null, null, null);
}
