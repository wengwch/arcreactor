package cn.veryai.arcreactor.actor.hypervisor;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Public hypervisor module facade used by instance lifecycle coordinators. */
public interface HypervisorPlacement {
    CompletionStage<ScheduleResult> schedule(ScheduleRequest request);

    CompletionStage<ReservationResult> confirm(String hypervisorId, String reservationId, String instanceId);

    CompletionStage<ReservationResult> cancel(String hypervisorId, String reservationId);

    record Resources(int vcpus, long memoryMb, int gpuCount, Set<String> requiredGpuTraits)
            implements Serializable {
        public Resources {
            if (vcpus < 0 || memoryMb < 0 || gpuCount < 0) {
                throw new IllegalArgumentException("resource values must not be negative");
            }
            requiredGpuTraits = Set.copyOf(Objects.requireNonNullElse(requiredGpuTraits, Set.of()));
        }
    }

    record ScheduleRequest(
            String requestId,
            String reservationId,
            String instanceId,
            Resources resources,
            Duration reservationTtl,
            String availabilityZone,
            Set<String> requiredTraits) implements Serializable {
        public ScheduleRequest {
            requireText(requestId, "requestId");
            requireText(reservationId, "reservationId");
            requireText(instanceId, "instanceId");
            Objects.requireNonNull(resources, "resources");
            if (reservationTtl == null || reservationTtl.isNegative() || reservationTtl.isZero()) {
                throw new IllegalArgumentException("reservationTtl must be positive");
            }
            requiredTraits = Set.copyOf(Objects.requireNonNullElse(requiredTraits, Set.of()));
        }
    }

    record ScheduleResult(Status status, String hypervisorId, int attempts, String detail)
            implements Serializable {
        public enum Status { RESERVED, NO_VALID_HOST }
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
