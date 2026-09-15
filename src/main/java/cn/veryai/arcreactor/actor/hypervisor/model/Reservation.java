package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public record Reservation(
        String reservationId,
        String requestId,
        String instanceId,
        ResourceRequest resources,
        Instant expiresAt) implements Serializable {
    public Reservation {
        requireText(reservationId, "reservationId");
        requireText(requestId, "requestId");
        requireText(instanceId, "instanceId");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
