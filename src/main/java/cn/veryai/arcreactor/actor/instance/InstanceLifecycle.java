package cn.veryai.arcreactor.actor.instance;

import cn.veryai.arcreactor.actor.instance.model.ForwardingRequirement;
import cn.veryai.arcreactor.actor.instance.model.CloudInitSpec;
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

    /** Submit only READY shared dependencies and resolved network IDs; memory is measured in GB. */
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
            long memoryGb,
            int gpuCount,
            Map<String, String> metadata,
            ShareRequirement share,
            ForwardingRequirement forwarding,
            NetworkRequirement network,
            CloudInitSpec cloudInit,
            String hypervisorType) implements Serializable {
        public CreateRequest {
            metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
            Objects.requireNonNull(share, "share");
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
