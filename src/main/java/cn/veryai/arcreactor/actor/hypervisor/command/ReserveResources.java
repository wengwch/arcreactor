package cn.veryai.arcreactor.actor.hypervisor.command;

import cn.veryai.arcreactor.actor.hypervisor.model.ResourceRequest;
import cn.veryai.arcreactor.actor.hypervisor.reply.ReserveReply;
import org.apache.pekko.actor.typed.ActorRef;

import java.time.Duration;
import java.util.Objects;

public record ReserveResources(String requestId, String reservationId, String instanceId,
        ResourceRequest resources, Duration ttl, ActorRef<ReserveReply> replyTo,
        String availabilityZone) implements HypervisorCommand {
    public ReserveResources {
        requireText(requestId, "requestId");
        requireText(reservationId, "reservationId");
        requireText(instanceId, "instanceId");
        Objects.requireNonNull(resources, "resources");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        Objects.requireNonNull(replyTo, "replyTo");
    }
    public ReserveResources(String requestId, String reservationId, String instanceId,
            ResourceRequest resources, Duration ttl, ActorRef<ReserveReply> replyTo) {
        this(requestId, reservationId, instanceId, resources, ttl, replyTo, null);
    }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
