package cn.veryai.arcreactor.actor.hypervisor.model;

import java.io.Serializable;

/** Occupancy imported while drained, including host reservations and unmanaged instances. */
public record ResourceUsage(int vcpus, long memoryGb, long diskGb, int gpus) implements Serializable {
    public ResourceUsage {
        if (vcpus < 0 || memoryGb < 0 || diskGb < 0 || gpus < 0) {
            throw new IllegalArgumentException("resource usage must not be negative");
        }
    }

    public static ResourceUsage empty() { return new ResourceUsage(0, 0, 0, 0); }
}
