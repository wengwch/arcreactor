package cn.veryai.arcreactor.actor.instance.model;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/** Immutable creation requirements, before dependencies are prepared. Memory and disk are measured in GB. */
public record InstanceSpec(
        String clusterId,
        String region,
        String name,
        String osImageId,
        String osFlavorId,
        int vcpus,
        long memoryGb,
        long diskGb,
        int gpuCount,
        Map<String, String> metadata,
        ShareRequirement share,
        ForwardingRequirement forwarding,
        NetworkRequirement network,
        CloudInitSpec cloudInit,
        String hypervisorType,
        String hostname) implements Serializable {
    public InstanceSpec {
        requireText(clusterId, "clusterId");
        requireText(region, "region");
        requireText(name, "name");
        requireText(osImageId, "osImageId");
        requireText(osFlavorId, "osFlavorId");
        requireText(hypervisorType, "hypervisorType");
        hostname = StringUtils.trimToNull(hostname);
        if (vcpus <= 0 || memoryGb <= 0 || diskGb < 0 || gpuCount < 0) {
            throw new IllegalArgumentException("invalid instance resources");
        }
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        Objects.requireNonNull(share, "share");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(cloudInit, "cloudInit");
        if (!region.equals(share.spec().regionId())) {
            throw new IllegalArgumentException("share must belong to the instance region");
        }
        if (network.userNetwork() == null || !region.equals(network.userNetwork().regionId())) {
            throw new IllegalArgumentException("regional user network requirements are required");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
