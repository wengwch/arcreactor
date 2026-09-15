package cn.veryai.arcreactor.actor.hypervisor.reply;

import java.time.Instant;

public record ReserveAccepted(String reservationId, Instant expiresAt, boolean idempotent) implements ReserveReply {}
