package cn.veryai.arcreactor.openstack;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Asynchronous Nova boundary used by the instance workflow module. */
public interface NovaClient {

    record ServerTarget(String clusterId, String projectId, String region) implements Serializable {
        public ServerTarget {
            requireText(clusterId, "clusterId");
            requireText(projectId, "projectId");
        }
    }

    record CreateServerRequest(
            ServerTarget target,
            String instanceId,
            String requestId,
            String operationId,
            String name,
            String imageId,
            String flavorId,
            String hypervisorId,
            String availabilityZone,
            Map<String, String> metadata) implements Serializable {
        public CreateServerRequest {
            Objects.requireNonNull(target, "target");
            requireText(instanceId, "instanceId");
            requireText(requestId, "requestId");
            requireText(operationId, "operationId");
            requireText(name, "name");
            requireText(imageId, "imageId");
            requireText(flavorId, "flavorId");
            requireText(hypervisorId, "hypervisorId");
            metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        }
    }

    record CreateServerResult(String serverId) implements Serializable {
        public CreateServerResult { requireText(serverId, "serverId"); }
    }

    record NovaServer(String serverId, Status status, String fault) implements Serializable {
        public NovaServer {
            requireText(serverId, "serverId");
            Objects.requireNonNull(status, "status");
        }

        public enum Status { BUILD, ACTIVE, ERROR, OTHER }
    }

    record ServerLookupResult(Status status, NovaServer server) implements Serializable {
        public enum Status { FOUND, NOT_FOUND }

        public ServerLookupResult {
            Objects.requireNonNull(status, "status");
            if ((status == Status.FOUND) != (server != null)) {
                throw new IllegalArgumentException("FOUND must include exactly one server");
            }
        }

        public static ServerLookupResult found(NovaServer server) {
            return new ServerLookupResult(Status.FOUND, Objects.requireNonNull(server));
        }

        public static ServerLookupResult notFound() {
            return new ServerLookupResult(Status.NOT_FOUND, null);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
