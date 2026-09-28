package cn.veryai.arcreactor.actor.instance;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Public facade for submitting instance creation workflows and querying their authoritative phase. */
public interface InstanceLifecycle {
    CompletionStage<Submission> create(CreateRequest request);

    CompletionStage<View> get(String instanceId);

    record CreateRequest(
            String requestId,
            String instanceId,
            String clusterId,
            String projectId,
            String region,
            String name,
            String imageId,
            String flavorId,
            int vcpus,
            long memoryMb,
            int gpuCount,
            Set<String> requiredGpuTraits,
            String availabilityZone,
            Set<String> requiredTraits,
            Map<String, String> metadata) implements Serializable {
        public CreateRequest {
            requiredGpuTraits = Set.copyOf(Objects.requireNonNullElse(requiredGpuTraits, Set.of()));
            requiredTraits = Set.copyOf(Objects.requireNonNullElse(requiredTraits, Set.of()));
            metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        }
    }

    record Submission(boolean accepted, boolean idempotent, String detail) implements Serializable {
    }

    record View(
            String instanceId,
            String requestId,
            String phase,
            String hypervisorId,
            String reservationId,
            String novaOperationId,
            String novaServerId,
            String novaStatus,
            String failureCode,
            String failureMessage,
            long version) implements Serializable {
    }
}
