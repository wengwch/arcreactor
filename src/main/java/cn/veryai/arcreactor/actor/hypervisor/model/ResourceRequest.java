package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;

public record ResourceRequest(
        int vcpus,
        long memoryMb,
        int gpuRequest) implements Serializable {

    public ResourceRequest {
        if (vcpus < 0) throw new IllegalArgumentException("vcpus must be non-negative");
        if (memoryMb < 0) throw new IllegalArgumentException("memoryMb must be non-negative");
        if (gpuRequest < 0) throw new IllegalArgumentException("gpuRequest must be non-negative");
    }
}
