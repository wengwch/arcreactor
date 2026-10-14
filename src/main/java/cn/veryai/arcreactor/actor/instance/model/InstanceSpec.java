package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public record InstanceSpec(
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
    public InstanceSpec {
        requireText(clusterId, "clusterId");
        requireText(projectId, "projectId");
        requireText(name, "name");
        requireText(imageId, "imageId");
        requireText(flavorId, "flavorId");
        if (vcpus < 0 || memoryMb < 0 || gpuCount < 0) {
            throw new IllegalArgumentException("resource values must not be negative");
        }
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        // A null dependency is retained only for replaying pre-share workflow events/snapshots.
        if (share != null) {
            if (!projectId.equals(share.spec().osProjectId())) {
                throw new IllegalArgumentException("share must belong to the instance project");
            }
            if (region == null || region.isBlank()) region = share.spec().regionId();
            if (!region.equals(share.spec().regionId())) {
                throw new IllegalArgumentException("share must belong to the instance region");
            }
        }
    }

    public InstanceSpec(String clusterId, String projectId, String region, String name,
            String imageId, String flavorId, int vcpus, long memoryMb, int gpuCount,
            Map<String, String> metadata, ShareRequirement share) {
        this(clusterId, projectId, region, name, imageId, flavorId, vcpus, memoryMb, gpuCount,
                metadata, share, null);
    }

    public InstanceSpec(String clusterId, String projectId, String region, String name,
            String imageId, String flavorId, int vcpus, long memoryMb, int gpuCount,
            Map<String, String> metadata) {
        this(clusterId, projectId, region, name, imageId, flavorId, vcpus, memoryMb, gpuCount,
                metadata, null, null);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
