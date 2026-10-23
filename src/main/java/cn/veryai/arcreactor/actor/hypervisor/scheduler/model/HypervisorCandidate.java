package cn.veryai.arcreactor.actor.hypervisor.scheduler.model;

import cn.veryai.arcreactor.enums.HypervisorStatus;
import java.io.Serializable;
import java.util.Objects;

public record HypervisorCandidate(String hypervisorId, HypervisorStatus status, String availabilityZone,
        int totalVcpus, int reservedVcpus, int allocatedVcpus,
        long totalMemoryGb, long reservedMemoryGb, long allocatedMemoryGb,
        int totalGpus, int reservedGpus, int allocatedGpus,
        long resourceVersion) implements Serializable {
    public HypervisorCandidate {
        Objects.requireNonNull(hypervisorId);
        Objects.requireNonNull(status);
        availabilityZone = Objects.requireNonNullElse(availabilityZone, "default");
    }
    public int availableVcpus() { return totalVcpus - reservedVcpus - allocatedVcpus; }
    public long availableMemoryGb() { return totalMemoryGb - reservedMemoryGb - allocatedMemoryGb; }
    public int availableGpuCount() { return totalGpus - reservedGpus - allocatedGpus; }
}
