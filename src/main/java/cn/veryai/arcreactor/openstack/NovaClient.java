package cn.veryai.arcreactor.openstack;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/** Asynchronous Nova operations required by the instance workflow. */
public interface NovaClient {
    CompletionStage<NovaServer> createServer(CreateServerRequest request);

    CompletionStage<NovaServer> getServer(ServerTarget target, String serverId);

    CompletionStage<ServerLookupResult> findServerByOperationId(ServerTarget target, String operationId);

    CompletionStage<Void> deleteServer(ServerTarget target, String serverId);

    record ServerTarget(String clusterId, String projectId, String region) implements Serializable {}

    record CreateServerRequest(ServerTarget target, String instanceId, String requestId,
            String operationId, String name, String imageId, String flavorId,
            String hypervisorId, String availabilityZone, Map<String, String> metadata)
            implements Serializable {
        public CreateServerRequest {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    record NovaServer(String serverId, Status status, String fault) implements Serializable {
        public enum Status { BUILD, ACTIVE, ERROR, OTHER }
    }

    record ServerLookupResult(Status status, NovaServer server) implements Serializable {
        public enum Status { FOUND, NOT_FOUND }
    }
}
