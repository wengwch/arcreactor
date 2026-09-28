package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        Set<String> requiredGpuTraits,
        String availabilityZone,
        Set<String> requiredTraits,
        Map<String, String> metadata) implements Serializable {
    public InstanceSpec {
        requireText(clusterId, "clusterId");
        requireText(projectId, "projectId");
        requireText(name, "name");
        requireText(imageId, "imageId");
        requireText(flavorId, "flavorId");
        if (vcpus < 0 || memoryMb < 0 || gpuCount < 0) {
            throw new IllegalArgumentException("resource values must not be negative");
        }
        requiredGpuTraits = Set.copyOf(Objects.requireNonNullElse(requiredGpuTraits, Set.of()));
        requiredTraits = Set.copyOf(Objects.requireNonNullElse(requiredTraits, Set.of()));
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
