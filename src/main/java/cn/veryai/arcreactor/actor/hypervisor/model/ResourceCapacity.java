package cn.veryai.arcreactor.actor.hypervisor.model;

import cn.veryai.arcreactor.util.MemoryUnits;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;

/** Hypervisor capacity; memory is measured in GB. */
public record ResourceCapacity(
        int totalVcpus,
        long totalMemoryGb,
        int gpus,
        String availabilityZone) implements Serializable {

    public ResourceCapacity {
        if (totalVcpus < 0) throw new IllegalArgumentException("totalVcpus must be non-negative");
        if (totalMemoryGb < 0) throw new IllegalArgumentException("totalMemoryGb must be non-negative");
        if (gpus < 0) throw new IllegalArgumentException("gpus must be non-negative");
        availabilityZone = Objects.requireNonNullElse(availabilityZone, "default");
    }

    public ResourceCapacity(int totalVcpus, long totalMemoryGb, int gpus) {
        this(totalVcpus, totalMemoryGb, gpus, "default");
    }

    @JsonCreator
    public static ResourceCapacity fromJson(
            @JsonProperty("totalVcpus") int totalVcpus,
            @JsonProperty("totalMemoryGb") Long totalMemoryGb,
            @JsonProperty("totalMemoryMb") Long legacyTotalMemoryMb,
            @JsonProperty("gpus") int gpus,
            @JsonProperty("availabilityZone") String availabilityZone) {
        return new ResourceCapacity(totalVcpus,
                MemoryUnits.readGigabytes(totalMemoryGb, legacyTotalMemoryMb), gpus, availabilityZone);
    }

    public static ResourceCapacity empty() {
        return new ResourceCapacity(0, 0, 0);
    }
}
