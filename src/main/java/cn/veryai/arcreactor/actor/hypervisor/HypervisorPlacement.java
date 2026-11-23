package cn.veryai.arcreactor.actor.hypervisor;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.StringUtils;

/** Public hypervisor module facade used by instance lifecycle coordinators. */
public interface HypervisorPlacement {
    /** Returns ranked candidates without reserving resources. */
    CompletionStage<ScheduleResult> schedule(ScheduleRequest request);

    /** Reserves only the specified host; callers must persist that host before invoking this method. */
    CompletionStage<ReserveResult> reserve(String hypervisorId, ScheduleRequest request);

    CompletionStage<ReservationResult> confirm(String hypervisorId, String reservationId, String instanceId);

    CompletionStage<ReservationResult> cancel(String hypervisorId, String reservationId);

    CompletionStage<ReservationResult> release(String hypervisorId, String instanceId);

    /** Memory and disk are measured in GB. */
    record Resources(int vcpus, long memoryGb, long diskGb, int gpuCount)
            implements Serializable {
        public Resources {
            if (vcpus < 0 || memoryGb < 0 || diskGb < 0 || gpuCount < 0) {
                throw new IllegalArgumentException("resource values must not be negative");
            }
        }
    }

    record ScheduleRequest(
            String requestId,
            String reservationId,
            String instanceId,
            Resources resources,
            String regionId,
            String hypervisorType,
            String hostname) implements Serializable {
        public ScheduleRequest {
            requireText(requestId, "requestId");
            requireText(reservationId, "reservationId");
            requireText(instanceId, "instanceId");
            Objects.requireNonNull(resources, "resources");
            hostname = StringUtils.trimToNull(hostname);
        }
    }

    /** Keeps the scheduled hostname paired with its resource-accounting actor ID. */
    record Candidate(String hypervisorId, String hostname) implements Serializable {}

    record ScheduleResult(List<Candidate> candidates, String detail) implements Serializable {
        public ScheduleResult {
            candidates = List.copyOf(candidates);
        }
    }

    record ReserveResult(Status status, String detail) implements Serializable {
        public enum Status { ACCEPTED, UNAVAILABLE, REJECTED, UNKNOWN }
    }

    record ReservationResult(Status status, boolean idempotent, String detail) implements Serializable {
        public enum Status { SUCCEEDED, REJECTED, UNKNOWN }

        public static ReservationResult succeeded(boolean idempotent, String detail) {
            return new ReservationResult(Status.SUCCEEDED, idempotent, detail);
        }

        public static ReservationResult rejected(String detail) {
            return new ReservationResult(Status.REJECTED, false, detail);
        }

        public static ReservationResult unknown(String detail) {
            return new ReservationResult(Status.UNKNOWN, false, detail);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
