package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/** Instance configuration; memory is measured in GB. */
public record InstanceSpec(
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
    public InstanceSpec {
        requireText(clusterId, "clusterId");
        requireText(projectId, "projectId");
        requireText(region, "region");
        requireText(name, "name");
        requireText(imageId, "imageId");
        requireText(flavorId, "flavorId");
        requireText(hypervisorType, "hypervisorType");
        if (vcpus <= 0 || memoryGb <= 0 || gpuCount < 0) {
            throw new IllegalArgumentException("invalid instance resources");
        }
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        Objects.requireNonNull(share, "share");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(cloudInit, "cloudInit");
        if (!projectId.equals(share.spec().osProjectId()) || !region.equals(share.spec().regionId())) {
            throw new IllegalArgumentException("prepared share must belong to the instance project and region");
        }
        if (!network.resolved() || network.userNetwork() == null || !region.equals(network.userNetwork().regionId())) {
            throw new IllegalArgumentException("resolved regional network dependencies are required");
        }
        if (forwarding != null && forwarding.parentPortId() != null) {
            throw new IllegalArgumentException("forwarding must use the ports created by the instance workflow");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
