package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

public record ResourceRequest(
        int vcpus,
        long memoryMb,
        GpuRequest gpuRequest,
        Set<String> gpuIds) implements Serializable {

    public ResourceRequest {
        if (vcpus < 0) throw new IllegalArgumentException("vcpus must be non-negative");
        if (memoryMb < 0) throw new IllegalArgumentException("memoryMb must be non-negative");
        gpuRequest = Objects.requireNonNullElse(gpuRequest, GpuRequest.NONE);
        gpuIds = Set.copyOf(Objects.requireNonNullElse(gpuIds, Set.of()));
        if (!gpuIds.isEmpty() && gpuIds.size() != gpuRequest.count()) {
            throw new IllegalArgumentException("assigned GPU count differs from request");
        }
    }

    public ResourceRequest(int vcpus, long memoryMb, GpuRequest gpuRequest) {
        this(vcpus, memoryMb, gpuRequest, Set.of());
    }

    public ResourceRequest assignedTo(Set<String> ids) {
        return new ResourceRequest(vcpus, memoryMb, gpuRequest, ids);
    }
}
