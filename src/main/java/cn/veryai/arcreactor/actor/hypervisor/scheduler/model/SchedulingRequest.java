package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import cn.veryai.arcreactor.actor.hypervisor.model.ResourceRequest;
import java.io.Serializable;
import java.util.Objects;

public record SchedulingRequest(String requestId, String reservationId, String instanceId,
        ResourceRequest resources, PlacementConstraints constraints)
        implements Serializable {
    public SchedulingRequest {
        requireText(requestId, "requestId");
        requireText(reservationId, "reservationId");
        requireText(instanceId, "instanceId");
        Objects.requireNonNull(resources);
        constraints = Objects.requireNonNullElse(constraints, PlacementConstraints.NONE);
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
