package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import cn.veryai.arcreactor.actor.hypervisor.reply.ReserveRejected;
import java.io.Serializable;

public record ClaimResult(Status status, String reservationId, ReserveRejected.Reason rejectionReason,
        String detail) implements Serializable {
    public enum Status { ACCEPTED, REJECTED, TIMEOUT, FAILED }
    public static ClaimResult accepted(String reservationId) {
        return new ClaimResult(Status.ACCEPTED, reservationId, null, "accepted");
    }
    public static ClaimResult rejected(String reservationId, ReserveRejected.Reason reason, String detail) {
        return new ClaimResult(Status.REJECTED, reservationId, reason, detail);
    }
    public static ClaimResult timeout(String reservationId, String detail) {
        return new ClaimResult(Status.TIMEOUT, reservationId, null, detail);
    }
    public static ClaimResult failed(String reservationId, String detail) {
        return new ClaimResult(Status.FAILED, reservationId, null, detail);
    }
}
