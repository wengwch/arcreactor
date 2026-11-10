package cn.veryai.arcreactor.actor.hypervisor.reply;

public record ReserveAccepted(String reservationId, boolean idempotent) implements ReserveReply {}
