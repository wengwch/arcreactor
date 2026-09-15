package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import java.io.Serializable;

public record SchedulingResult(Status status, String hypervisorId, String reservationId,
        int claimAttempts, String detail) implements Serializable {
    public enum Status { RESERVED, NO_VALID_HOST }
    public static SchedulingResult reserved(String hypervisorId, String reservationId, int attempts) {
        return new SchedulingResult(Status.RESERVED, hypervisorId, reservationId, attempts, "reserved");
    }
    public static SchedulingResult noValidHost(String reservationId, int attempts, String detail) {
        return new SchedulingResult(Status.NO_VALID_HOST, null, reservationId, attempts, detail);
    }
}
