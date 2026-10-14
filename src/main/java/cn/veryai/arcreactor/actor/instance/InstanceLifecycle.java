package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.instance.model.ShareRequirement;
import cn.veryai.arcreactor.actor.instance.model.ForwardingRequirement;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
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
            Map<String, String> metadata,
            ShareRequirement share,
            ForwardingRequirement forwarding) implements Serializable {
        public CreateRequest {
            metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
            Objects.requireNonNull(share, "share");
        }
        public CreateRequest(String requestId, String instanceId, String clusterId, String projectId,
                String region, String name, String imageId, String flavorId, int vcpus, long memoryMb,
                int gpuCount, Map<String, String> metadata, ShareRequirement share) {
            this(requestId, instanceId, clusterId, projectId, region, name, imageId, flavorId,
                    vcpus, memoryMb, gpuCount, metadata, share, null);
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
            long version,
            String userId,
            String shareId,
            String osShareId,
            String floatingIp,
            int externalPort) implements Serializable {
    }
}
