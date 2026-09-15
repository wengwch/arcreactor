package cn.veryai.arcreactor.actor.hypervisor.reply;

public record ReserveRejected(String reservationId, Reason reason, String detail) implements ReserveReply {
    public enum Reason {
        NOT_ACTIVE,
        INSUFFICIENT_RESOURCE,
        INSTANCE_ALREADY_ALLOCATED,
        RESERVATION_ID_CONFLICT,
        RESERVATION_FINALIZED,
        INVALID_REQUEST
    }
}
