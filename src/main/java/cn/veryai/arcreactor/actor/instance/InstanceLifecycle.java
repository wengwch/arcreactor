package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.instance.model.ForwardingRequirement;
import cn.veryai.arcreactor.actor.instance.model.NetworkRequirement;
import cn.veryai.arcreactor.actor.instance.model.PreparedNetwork;
import cn.veryai.arcreactor.actor.instance.model.ShareRequirement;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Public facade for submitting instance creation workflows and querying their authoritative phase. */
public interface InstanceLifecycle {
    CompletionStage<Submission> create(CreateRequest request);

    CompletionStage<View> get(String instanceId);

    /**
     * Project IDs can be omitted. New projects use projectName or the share's user ID; existing projects are reused.
     * Network requirements must include a userNetwork specification; subport network/subnet IDs are resolved after READY.
     * Parent network/subnet IDs are resolved from the region's preset SHARED network.
     */
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
            ForwardingRequirement forwarding,
            NetworkRequirement network,
            String projectName) implements Serializable {
        public CreateRequest {
            metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
            Objects.requireNonNull(share, "share");
        }
        public CreateRequest(String requestId, String instanceId, String clusterId, String projectId,
                String region, String name, String imageId, String flavorId, int vcpus, long memoryMb,
                int gpuCount, Map<String, String> metadata, ShareRequirement share,
                ForwardingRequirement forwarding, NetworkRequirement network) {
            this(requestId, instanceId, clusterId, projectId, region, name, imageId, flavorId,
                    vcpus, memoryMb, gpuCount, metadata, share, forwarding, network, null);
        }
        public CreateRequest(String requestId, String instanceId, String clusterId, String projectId,
                String region, String name, String imageId, String flavorId, int vcpus, long memoryMb,
                int gpuCount, Map<String, String> metadata, ShareRequirement share, ForwardingRequirement forwarding) {
            this(requestId, instanceId, clusterId, projectId, region, name, imageId, flavorId,
                    vcpus, memoryMb, gpuCount, metadata, share, forwarding, null);
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
            int externalPort,
            PreparedNetwork network,
            String projectId) implements Serializable {
    }
}
