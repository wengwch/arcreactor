package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;

/** Requested resources; memory and disk are measured in GB. */
public record ResourceRequest(
        int vcpus,
        long memoryGb,
        long diskGb,
        int gpuRequest) implements Serializable {

    public ResourceRequest {
        if (vcpus < 0) throw new IllegalArgumentException("vcpus must be non-negative");
        if (memoryGb < 0) throw new IllegalArgumentException("memoryGb must be non-negative");
        if (diskGb < 0) throw new IllegalArgumentException("diskGb must be non-negative");
        if (gpuRequest < 0) throw new IllegalArgumentException("gpuRequest must be non-negative");
    }
}
