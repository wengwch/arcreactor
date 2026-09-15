package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public record Allocation(
        String instanceId,
        String reservationId,
        String requestId,
        ResourceRequest resources,
        Instant allocatedAt) implements Serializable {
    public Allocation {
        if (instanceId == null || instanceId.isBlank()) throw new IllegalArgumentException("instanceId is required");
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(allocatedAt, "allocatedAt");
    }
}
